package ma.rh.inventory;

import static ma.rh.inventory.Models.*;
import static ma.rh.inventory.StockLedger.BalanceKey;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;

@Service
public class StockService {
 private final JdbcClient jdbc;private final Access access;private final Catalogue catalogue;private final Commands commands;private final StockLedger ledger;
 public StockService(JdbcClient jdbc,Access access,Catalogue catalogue,Commands commands,StockLedger ledger){this.jdbc=jdbc;this.access=access;this.catalogue=catalogue;this.commands=commands;this.ledger=ledger;}
 public Operation opening(Jwt jwt,String key,Opening input){
  access.requireOwner(jwt);access.requireSite(input.branchId(),access.allowed(jwt));
  return commands.execute(jwt.getSubject(),key,"stock.opening",input,Operation.class,()->{
   Variant variant=catalogue.lock(input.variantId());active(variant);
   ledger.lock(List.of(new BalanceKey(input.branchId(),variant.id(),"SELLABLE")));
   if(jdbc.sql("SELECT EXISTS(SELECT 1 FROM stock_entries WHERE branch_id=:b AND variant_id=:v)").param("b",input.branchId()).param("v",variant.id()).query(Boolean.class).single())throw InventoryProblem.conflict("OPENING_ALREADY_RECORDED");
   UUID id=ledger.movement("OPENING",input.reason(),jwt,null);
   jdbc.sql("INSERT INTO stock_openings(branch_id,variant_id,movement_id) VALUES(:b,:v,:m)").param("b",input.branchId()).param("v",variant.id()).param("m",id).update();
   ledger.entry(id,input.branchId(),variant,"SELLABLE",input.quantity(),input.unitCost());
   return new Operation(id,"OPENING");
  });
 }
 public Operation adjust(Jwt jwt,String key,Adjustment input){
  access.requireOwner(jwt);access.requireSite(input.branchId(),access.allowed(jwt));if(input.delta()==0)throw InventoryProblem.invalid("ZERO_ADJUSTMENT");
  return commands.execute(jwt.getSubject(),key,"stock.adjustment",input,Operation.class,()->{
   var variant=catalogue.lock(input.variantId());ledger.lock(List.of(new BalanceKey(input.branchId(),variant.id(),"SELLABLE")));
   UUID id=ledger.movement("ADJUSTMENT",input.reason(),jwt,null);ledger.entry(id,input.branchId(),variant,"SELLABLE",input.delta(),null);return new Operation(id,"ADJUSTMENT");
  });
 }
 static void active(Variant variant){if(!variant.active())throw InventoryProblem.conflict("VARIANT_ARCHIVED");}
 List<UUID> scope(Jwt jwt,UUID branch){
  var allowed=access.allowed(jwt);if(branch!=null){access.requireSite(branch,allowed);return List.of(branch);}return allowed.stream().map(Site::id).toList();
 }
 public Page<StockRow> balances(Jwt jwt,UUID branch,String q,int page,int size){
  List<UUID> scope=scope(jwt,branch);var variants=catalogue.variants(q,page,size);
  if(scope.isEmpty()||variants.items().isEmpty())return new Page<>(List.of(),page,size,variants.total());
  var ids=variants.items().stream().map(Variant::id).toList();
  Map<String,Long> balances=new HashMap<>(),incoming=new HashMap<>();
  jdbc.sql("SELECT branch_id,variant_id,bucket,quantity FROM stock_balances WHERE branch_id IN(:b) AND variant_id IN(:v)").param("b",scope).param("v",ids).query((rs,n)->{
   balances.put(rs.getObject(1,UUID.class)+"|"+rs.getObject(2,UUID.class)+"|"+rs.getString(3),rs.getLong(4));return true;
  }).list();
  jdbc.sql("SELECT t.destination_id,l.variant_id,sum(l.quantity-l.received-l.lost) AS incoming FROM transfer_lines l JOIN stock_transfers t ON t.id=l.transfer_id WHERE t.destination_id IN(:b) AND l.variant_id IN(:v) GROUP BY t.destination_id,l.variant_id").param("b",scope).param("v",ids).query((rs,n)->{incoming.put(rs.getObject(1,UUID.class)+"|"+rs.getObject(2,UUID.class),rs.getLong(3));return true;}).list();
  List<StockRow> result=new ArrayList<>();
  for(var variant:variants.items())for(UUID site:scope){
   String balanceKey=site+"|"+variant.id()+"|SELLABLE";long qty=balances.getOrDefault(balanceKey,0L);boolean initialized=balances.containsKey(balanceKey);
   result.add(new StockRow(site,variant,qty,balances.getOrDefault(site+"|"+variant.id()+"|TRANSIT",0L),incoming.getOrDefault(site+"|"+variant.id(),0L),initialized,initialized&&variant.active()&&variant.minimumStock()>0&&qty<variant.minimumStock(),balances.getOrDefault(site+"|"+variant.id()+"|QUARANTINE",0L),balances.getOrDefault(site+"|"+variant.id()+"|REJECTED",0L)));
  }
  return new Page<>(result,page,size,variants.total());
 }
 public Page<MovementRow> movements(Jwt jwt,UUID branch,UUID variant,int page,int size){
  Catalogue.page(page);Catalogue.size(size);var scope=scope(jwt,branch);
  if(scope.isEmpty())return new Page<>(List.of(),page,size,0);
  String filter=" WHERE e.branch_id IN(:b)"+(variant==null?"":" AND e.variant_id=:v");
  var query=jdbc.sql("SELECT m.id,e.id AS entry_id,m.kind,m.reason,m.actor,to_char(m.created_at AT TIME ZONE 'UTC','YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"') AS created_at,e.branch_id,e.variant_id,e.sku,e.label,e.bucket,e.delta,e.quantity_after,CASE WHEN :owner THEN e.unit_cost ELSE NULL END AS unit_cost,m.transfer_id FROM stock_entries e JOIN stock_movements m ON m.id=e.movement_id"+filter+" ORDER BY m.created_at DESC,e.id LIMIT :n OFFSET :o")
   .param("b",scope).param("owner",access.owner(jwt)).param("n",size).param("o",(long)page*size);
  if(variant!=null)query.param("v",variant);
  var items=query.query(MovementRow.class).list();
  var count=jdbc.sql("SELECT count(*) FROM stock_entries e"+filter).param("b",scope);if(variant!=null)count.param("v",variant);
  return new Page<>(items,page,size,count.query(Long.class).single());
 }
}
