package ma.rh.inventory;
import static ma.rh.inventory.PurchaseModels.*;
import static ma.rh.inventory.Models.*;
import static ma.rh.inventory.StockLedger.BalanceKey;
import java.util.*;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
@Service
public class Purchases {
 private final JdbcClient jdbc; private final Access access; private final Commands commands; private final Catalogue catalogue; private final StockLedger ledger; private final SupplierAccess suppliers;
 private static final String DATE="to_char(created_at AT TIME ZONE 'UTC','YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"') AS created_at";
 public Purchases(JdbcClient jdbc,Access access,Commands commands,Catalogue catalogue,StockLedger ledger,SupplierAccess suppliers){this.jdbc=jdbc;this.access=access;this.commands=commands;this.catalogue=catalogue;this.ledger=ledger;this.suppliers=suppliers;}
 Header header(UUID id,boolean lock){return jdbc.sql("SELECT id,supplier_id,supplier_name,branch_id,branch_name,reference,note,closed,version,"+DATE+" FROM purchase_orders WHERE id=:id"+(lock?" FOR UPDATE":"")).param("id",id).query(Header.class).optional().orElseThrow(()->new InventoryProblem(HttpStatus.NOT_FOUND,"PURCHASE_NOT_FOUND"));}
 void scoped(Jwt jwt,Header h){access.requireManager(jwt);access.requireSite(h.branchId(),access.allowed(jwt));}
 List<RawLine> lines(UUID id){return jdbc.sql("SELECT variant_id,sku,label,quantity,received,unit_cost FROM purchase_lines WHERE order_id=:id ORDER BY sku").param("id",id).query(RawLine.class).list();}
 public Order get(Jwt jwt,UUID id){Header h=header(id,false);scoped(jwt,h);return view(jwt,h);}
 Order view(Jwt jwt,Header h){
  var raw=lines(h.id());var items=raw.stream().map(l->new OrderLine(l.variantId(),l.sku(),l.label(),l.quantity(),l.received(),l.quantity()-l.received(),access.owner(jwt)?l.unitCost():null)).toList();
  long remaining=raw.stream().mapToLong(l->l.quantity()-l.received()).sum(),received=raw.stream().mapToLong(RawLine::received).sum();
  String status=h.closed()?"CLOSED":remaining==0?"RECEIVED":received>0?"PARTIAL":"OPEN";
  var receipts=jdbc.sql("SELECT id FROM purchase_receipts WHERE order_id=:id ORDER BY created_at,id").param("id",h.id()).query(UUID.class).list().stream().map(this::delivery).toList();
  return new Order(h.id(),h.supplierId(),h.supplierName(),h.branchId(),h.branchName(),h.reference(),h.note(),status,h.version(),h.createdAt(),items,receipts);
 }
 Delivery delivery(UUID id){
  var row=jdbc.sql("SELECT reference,reason,actor,"+DATE+" FROM purchase_receipts WHERE id=:id").param("id",id).query((rs,n)->new String[]{rs.getString("reference"),rs.getString("reason"),rs.getString("actor"),rs.getString("created_at")}).single();
  var items=jdbc.sql("SELECT variant_id,sku,label,quantity,accepted,rejected,quantity-accepted-rejected AS pending FROM purchase_receipt_lines WHERE receipt_id=:id ORDER BY sku").param("id",id).query(DeliveryLine.class).list();
  return new Delivery(id,row[0],row[1],row[2],row[3],items);
 }
 public Page<Order> list(Jwt jwt,String q,int page,int size){
  access.requireManager(jwt);Catalogue.page(page);Catalogue.size(size);var scope=access.allowed(jwt).stream().map(Site::id).toList();if(scope.isEmpty())return new Page<>(List.of(),page,size,0);
  String filter=" WHERE branch_id IN(:b) AND (reference ILIKE :q OR supplier_name ILIKE :q)";
  var ids=jdbc.sql("SELECT id FROM purchase_orders"+filter+" ORDER BY created_at DESC,id LIMIT :n OFFSET :o").param("b",scope).param("q",Catalogue.search(q)).param("n",size).param("o",(long)page*size).query(UUID.class).list();
  long total=jdbc.sql("SELECT count(*) FROM purchase_orders"+filter).param("b",scope).param("q",Catalogue.search(q)).query(Long.class).single();
  return new Page<>(ids.stream().map(id->view(jwt,header(id,false))).toList(),page,size,total);
 }
 private <T> List<T> unique(List<T> items,Function<T,UUID> id){var seen=new HashSet<UUID>();for(var item:items)if(!seen.add(id.apply(item)))throw InventoryProblem.invalid("DUPLICATE_PURCHASE_LINE");return items;}
 public Order create(Jwt jwt,String key,OrderInput input){
  access.requireOwner(jwt);var sites=access.allowed(jwt);access.requireSite(input.branchId(),sites);

  unique(input.items(),OrderItem::variantId);
  return commands.execute(jwt.getSubject(),key,"purchase.create",input,Order.class,()->{
  var supplier=suppliers.get(jwt,input.supplierId());if(!supplier.active())throw InventoryProblem.conflict("SUPPLIER_ARCHIVED");
   UUID id=UUID.randomUUID();String branch=sites.stream().filter(s->s.id().equals(input.branchId())).findFirst().orElseThrow().name();
   jdbc.sql("INSERT INTO purchase_orders(id,supplier_id,supplier_name,branch_id,branch_name,reference,note,actor) VALUES(:id,:s,:sn,:b,:bn,:r,:n,:a)")
    .param("id",id).param("s",supplier.id()).param("sn",supplier.name()).param("b",input.branchId()).param("bn",branch).param("r",Catalogue.text(input.reference())).param("n",Catalogue.text(input.note())).param("a",jwt.getSubject()).update();
   for(var item:input.items().stream().sorted(Comparator.comparing(i->i.variantId().toString())).toList()){
    var v=catalogue.lock(item.variantId());StockService.active(v);
    jdbc.sql("INSERT INTO purchase_lines(order_id,variant_id,sku,label,quantity,unit_cost) VALUES(:o,:v,:s,:l,:q,:c)").param("o",id).param("v",v.id()).param("s",v.sku()).param("l",Catalogue.label(v)).param("q",item.quantity()).param("c",item.unitCost()).update();
   }
   return view(jwt,header(id,false));
  });
 }
 public Delivery receive(Jwt jwt,String key,UUID orderId,ReceiptInput input){
  Header initial=header(orderId,false);scoped(jwt,initial);unique(input.items(),Models.ReceiptItem::variantId);
  return commands.execute(jwt.getSubject(),key,"purchase.receive:"+orderId,input,Delivery.class,()->{
   Header h=header(orderId,true);if(h.closed())throw InventoryProblem.conflict("PURCHASE_CLOSED");
   Map<UUID,RawLine> raw=new HashMap<>();for(var line:lines(orderId))raw.put(line.variantId(),line);
   var sorted=input.items().stream().sorted(Comparator.comparing(i->i.variantId().toString())).toList();
   Map<UUID,Variant> variants=new HashMap<>();
   for(var item:sorted){var line=raw.get(item.variantId());if(line==null)throw InventoryProblem.invalid("PURCHASE_LINE_NOT_FOUND");if(item.quantity()>line.quantity()-line.received())throw InventoryProblem.conflict("RECEIPT_EXCEEDS_REMAINING");variants.put(item.variantId(),catalogue.lock(item.variantId()));}
   ledger.lock(sorted.stream().map(i->new BalanceKey(h.branchId(),i.variantId(),"QUARANTINE")).toList());
   UUID movement=ledger.movement("PURCHASE_RECEIPT",input.reason(),jwt,null),id=UUID.randomUUID();
   jdbc.sql("INSERT INTO purchase_receipts(id,order_id,reference,reason,actor,movement_id) VALUES(:id,:o,:r,:why,:a,:m)").param("id",id).param("o",orderId).param("r",Catalogue.text(input.reference())).param("why",Catalogue.text(input.reason())).param("a",jwt.getSubject()).param("m",movement).update();
   for(var item:sorted){var line=raw.get(item.variantId());
    jdbc.sql("UPDATE purchase_lines SET received=received+:q WHERE order_id=:o AND variant_id=:v").param("q",item.quantity()).param("o",orderId).param("v",item.variantId()).update();
    jdbc.sql("INSERT INTO purchase_receipt_lines(receipt_id,variant_id,sku,label,quantity) VALUES(:r,:v,:s,:l,:q)").param("r",id).param("v",item.variantId()).param("s",line.sku()).param("l",line.label()).param("q",item.quantity()).update();
    ledger.entry(movement,h.branchId(),variants.get(item.variantId()),"QUARANTINE",item.quantity(),line.unitCost());
   }
   jdbc.sql("UPDATE purchase_orders SET version=version+1 WHERE id=:id").param("id",orderId).update();
   return delivery(id);
  });
 }
 public Delivery inspect(Jwt jwt,String key,UUID orderId,UUID receiptId,InspectionInput input){
  access.requireOwner(jwt);scoped(jwt,header(orderId,false));unique(input.items(),InspectionItem::variantId);
  return commands.execute(jwt.getSubject(),key,"purchase.inspect:"+orderId+":"+receiptId,input,Delivery.class,()->{
   Header h=header(orderId,true);
   if(!jdbc.sql("SELECT EXISTS(SELECT 1 FROM purchase_receipts WHERE id=:r AND order_id=:o)").param("r",receiptId).param("o",orderId).query(Boolean.class).single())throw new InventoryProblem(HttpStatus.NOT_FOUND,"RECEIPT_NOT_FOUND");
   var delivery=delivery(receiptId);Map<UUID,DeliveryLine> raw=new HashMap<>();delivery.items().forEach(i->raw.put(i.variantId(),i));
   var sorted=input.items().stream().sorted(Comparator.comparing(i->i.variantId().toString())).toList();Map<UUID,Variant> variants=new HashMap<>();
   List<BalanceKey> locks=new ArrayList<>();
   for(var i:sorted){var line=raw.get(i.variantId());if(line==null||i.accepted()+i.rejected()==0)throw InventoryProblem.invalid("INVALID_INSPECTION");if(i.accepted()+i.rejected()>line.pending())throw InventoryProblem.conflict("INSPECTION_EXCEEDS_PENDING");variants.put(i.variantId(),catalogue.lock(i.variantId()));for(String bucket:List.of("QUARANTINE","SELLABLE","REJECTED"))locks.add(new BalanceKey(h.branchId(),i.variantId(),bucket));}
   ledger.lock(locks);UUID inspection=UUID.randomUUID();
   jdbc.sql("INSERT INTO purchase_inspections(id,receipt_id,reason,actor) VALUES(:id,:r,:why,:a)").param("id",inspection).param("r",receiptId).param("why",Catalogue.text(input.reason())).param("a",jwt.getSubject()).update();
   UUID accepted=null,rejected=null;
   if(sorted.stream().anyMatch(i->i.accepted()>0))accepted=ledger.movement("QUALITY_RELEASE",input.reason(),jwt,null);
   if(sorted.stream().anyMatch(i->i.rejected()>0))rejected=ledger.movement("QUALITY_REJECT",input.reason(),jwt,null);
   Map<UUID,RawLine> orderLines=new HashMap<>();lines(orderId).forEach(l->orderLines.put(l.variantId(),l));
   for(var i:sorted){var v=variants.get(i.variantId());var cost=orderLines.get(i.variantId()).unitCost();
    jdbc.sql("UPDATE purchase_receipt_lines SET accepted=accepted+:a,rejected=rejected+:x WHERE receipt_id=:r AND variant_id=:v").param("a",i.accepted()).param("x",i.rejected()).param("r",receiptId).param("v",i.variantId()).update();
    jdbc.sql("INSERT INTO purchase_inspection_lines(inspection_id,variant_id,accepted,rejected) VALUES(:id,:v,:a,:x)").param("id",inspection).param("v",i.variantId()).param("a",i.accepted()).param("x",i.rejected()).update();
    if(i.accepted()>0){ledger.entry(accepted,h.branchId(),v,"QUARANTINE",-i.accepted(),cost);ledger.entry(accepted,h.branchId(),v,"SELLABLE",i.accepted(),cost);}
    if(i.rejected()>0){ledger.entry(rejected,h.branchId(),v,"QUARANTINE",-i.rejected(),cost);ledger.entry(rejected,h.branchId(),v,"REJECTED",i.rejected(),cost);}
   }
   jdbc.sql("UPDATE purchase_orders SET version=version+1 WHERE id=:id").param("id",orderId).update();
   return delivery(receiptId);
  });
 }
 public Order close(Jwt jwt,String key,UUID orderId,CloseInput input){
  access.requireOwner(jwt);scoped(jwt,header(orderId,false));
  return commands.execute(jwt.getSubject(),key,"purchase.close:"+orderId,input,Order.class,()->{
   var h=header(orderId,true);if(h.closed())throw InventoryProblem.conflict("PURCHASE_CLOSED");
   jdbc.sql("UPDATE purchase_orders SET closed=true,version=version+1,close_reason=:r,closed_by=:a,closed_at=now() WHERE id=:id").param("r",Catalogue.text(input.reason())).param("a",jwt.getSubject()).param("id",orderId).update();
   return view(jwt,header(orderId,false));
  });
 }
}
