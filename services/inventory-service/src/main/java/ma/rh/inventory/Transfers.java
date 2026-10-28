package ma.rh.inventory;

import static ma.rh.inventory.Models.*;
import static ma.rh.inventory.StockLedger.BalanceKey;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;

@Service
public class Transfers {
 private final JdbcClient jdbc;private final Access access;private final Catalogue catalogue;private final Commands commands;private final StockLedger ledger;
 public Transfers(JdbcClient jdbc,Access access,Catalogue catalogue,Commands commands,StockLedger ledger){this.jdbc=jdbc;this.access=access;this.catalogue=catalogue;this.commands=commands;this.ledger=ledger;}
 static List<ReceiptItem> sorted(List<ReceiptItem> items){
  if(items.stream().map(ReceiptItem::variantId).distinct().count()!=items.size())throw InventoryProblem.invalid("DUPLICATE_TRANSFER_LINE");
  return items.stream().sorted(Comparator.comparing(i->i.variantId().toString())).toList();
 }
 private Map<UUID,Variant> lockVariants(List<ReceiptItem> items){
  Map<UUID,Variant> result=new LinkedHashMap<>();for(var item:items)result.put(item.variantId(),catalogue.lock(item.variantId()));return result;
 }
 public Transfer dispatch(Jwt jwt,String key,TransferInput input){
  access.requireManager(jwt);var allowed=access.allowed(jwt);access.requireSite(input.sourceId(),allowed);
  if(input.sourceId().equals(input.destinationId()))throw InventoryProblem.invalid("SAME_BRANCH");
  var source=allowed.stream().filter(s->s.id().equals(input.sourceId())).findFirst().orElseThrow(InventoryProblem::forbidden);
  var destination=access.directory(jwt).stream().filter(s->s.id().equals(input.destinationId())).findFirst().orElseThrow(()->InventoryProblem.invalid("DESTINATION_INVALID"));
  var items=sorted(input.items());
  return commands.execute(jwt.getSubject(),key,"stock.transfer.dispatch",new TransferInput(input.sourceId(),input.destinationId(),Catalogue.text(input.reason()),items),Transfer.class,()->{
   var variants=lockVariants(items);variants.values().forEach(StockService::active);
   var keys=new ArrayList<BalanceKey>();for(var item:items){keys.add(new BalanceKey(input.sourceId(),item.variantId(),"SELLABLE"));keys.add(new BalanceKey(input.sourceId(),item.variantId(),"TRANSIT"));}ledger.lock(keys);
   UUID id=UUID.randomUUID();
   jdbc.sql("INSERT INTO stock_transfers(id,source_id,destination_id,source_name,destination_name) VALUES(:id,:s,:d,:sn,:dn)").param("id",id).param("s",input.sourceId()).param("d",input.destinationId()).param("sn",source.name()).param("dn",destination.name()).update();
   UUID movement=ledger.movement("TRANSFER_OUT",input.reason(),jwt,id);
   for(var item:items){
    Variant v=variants.get(item.variantId());
    jdbc.sql("INSERT INTO transfer_lines(transfer_id,variant_id,sku,label,quantity) VALUES(:t,:v,:s,:l,:q)").param("t",id).param("v",v.id()).param("s",v.sku()).param("l",Catalogue.label(v)).param("q",item.quantity()).update();
    ledger.entry(movement,input.sourceId(),v,"SELLABLE",-item.quantity(),null);ledger.entry(movement,input.sourceId(),v,"TRANSIT",item.quantity(),null);
   }
   return read(id);
  });
 }
 private TransferHeader header(UUID id,boolean lock){
  return jdbc.sql("SELECT id,source_id,destination_id,source_name,destination_name,to_char(created_at AT TIME ZONE 'UTC','YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"') AS created_at FROM stock_transfers WHERE id=:id"+(lock?" FOR UPDATE":"")).param("id",id).query(TransferHeader.class).optional().orElseThrow(()->new InventoryProblem(HttpStatus.NOT_FOUND,"TRANSFER_NOT_FOUND"));
 }
 Transfer read(UUID id){
  var h=header(id,false);
  var lines=jdbc.sql("SELECT variant_id,sku,label,quantity,received,lost,(quantity-received-lost) AS remaining FROM transfer_lines WHERE transfer_id=:id ORDER BY sku").param("id",id).query(TransferLine.class).list();
  long remaining=lines.stream().mapToLong(TransferLine::remaining).sum(),received=lines.stream().mapToLong(TransferLine::received).sum(),lost=lines.stream().mapToLong(TransferLine::lost).sum();
  String status=remaining==0?(lost>0?"RESOLVED":"RECEIVED"):(received+lost>0?"PARTIAL":"OPEN");
  return new Transfer(h.id(),h.sourceId(),h.destinationId(),h.sourceName(),h.destinationName(),status,h.createdAt(),lines);
 }
 public Transfer get(Jwt jwt,UUID id){
  var allowed=access.allowed(jwt);var h=header(id,false);
  if(!access.owner(jwt)&&allowed.stream().noneMatch(s->s.id().equals(h.sourceId())||s.id().equals(h.destinationId())))throw InventoryProblem.forbidden();
  return read(id);
 }
 public Page<Transfer> list(Jwt jwt,int page,int size){
  Catalogue.page(page);Catalogue.size(size);var ids=access.allowed(jwt).stream().map(Site::id).toList();
  if(!access.owner(jwt)&&ids.isEmpty())return new Page<>(List.of(),page,size,0);
  String filter=access.owner(jwt)?"":" WHERE source_id IN(:sites) OR destination_id IN(:sites)";
  var query=jdbc.sql("SELECT id FROM stock_transfers"+filter+" ORDER BY created_at DESC,id LIMIT :n OFFSET :o").param("n",size).param("o",(long)page*size);
  var count=jdbc.sql("SELECT count(*) FROM stock_transfers"+filter);
  if(!access.owner(jwt)){query.param("sites",ids);count.param("sites",ids);}
  return new Page<>(query.query(UUID.class).list().stream().map(this::read).toList(),page,size,count.query(Long.class).single());
 }
 public Transfer receive(Jwt jwt,String key,UUID id,Receipt input,boolean loss){
  if(loss)access.requireOwner(jwt);else access.requireManager(jwt);
  var allowed=access.allowed(jwt);var h=header(id,false);
  if(!access.owner(jwt))access.requireSite(h.destinationId(),allowed);
  var items=sorted(input.items());var canonical=new Receipt(Catalogue.text(input.reason()),items);
  return commands.execute(jwt.getSubject(),key,"stock.transfer."+(loss?"loss:":"receive:")+id,canonical,Transfer.class,()->{
   var current=header(id,true);var transfer=read(id);
   var lines=transfer.items().stream().collect(Collectors.toMap(TransferLine::variantId,Function.identity()));
   for(var item:items){var line=lines.get(item.variantId());if(line==null||item.quantity()>line.remaining())throw InventoryProblem.conflict("RECEIPT_EXCEEDS_REMAINING");}
   var variants=lockVariants(items);var keys=new ArrayList<BalanceKey>();
   for(var item:items){keys.add(new BalanceKey(current.sourceId(),item.variantId(),"TRANSIT"));if(!loss)keys.add(new BalanceKey(current.destinationId(),item.variantId(),"SELLABLE"));}ledger.lock(keys);
   UUID movement=ledger.movement(loss?"TRANSIT_LOSS":"TRANSFER_IN",input.reason(),jwt,id);
   for(var item:items){
    var variant=variants.get(item.variantId());
    ledger.entry(movement,current.sourceId(),variant,"TRANSIT",-item.quantity(),null);
    if(!loss)ledger.entry(movement,current.destinationId(),variant,"SELLABLE",item.quantity(),null);
    String column=loss?"lost":"received";
    int changed=jdbc.sql("UPDATE transfer_lines SET "+column+"="+column+"+:q WHERE transfer_id=:t AND variant_id=:v AND quantity-received-lost>=:q").param("q",item.quantity()).param("t",id).param("v",variant.id()).update();
    if(changed!=1)throw InventoryProblem.conflict("RECEIPT_EXCEEDS_REMAINING");
   }
   return read(id);
  });
 }
}
