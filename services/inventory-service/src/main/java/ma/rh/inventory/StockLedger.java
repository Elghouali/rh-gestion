package ma.rh.inventory;

import static ma.rh.inventory.Models.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class StockLedger {
 private final JdbcClient jdbc;
 public StockLedger(JdbcClient jdbc){this.jdbc=jdbc;}
 record BalanceKey(UUID branch,UUID variant,String bucket) implements Comparable<BalanceKey>{
  public int compareTo(BalanceKey other){return (branch+"|"+variant+"|"+bucket).compareTo(other.branch+"|"+other.variant+"|"+other.bucket);}
 }
 void lock(Collection<BalanceKey> keys){
  for(var key:new TreeSet<>(keys)){
   jdbc.sql("INSERT INTO stock_balances(branch_id,variant_id,bucket,quantity) VALUES(:b,:v,:k,0) ON CONFLICT DO NOTHING").param("b",key.branch()).param("v",key.variant()).param("k",key.bucket()).update();
   jdbc.sql("SELECT quantity FROM stock_balances WHERE branch_id=:b AND variant_id=:v AND bucket=:k FOR UPDATE").param("b",key.branch()).param("v",key.variant()).param("k",key.bucket()).query(Long.class).single();
  }
 }
 UUID movement(String kind,String reason,Jwt jwt,UUID transfer){
  UUID id=UUID.randomUUID();
  jdbc.sql("INSERT INTO stock_movements(id,kind,reason,actor,transfer_id) VALUES(:id,:k,:r,:a,:t)").param("id",id).param("k",kind).param("r",Catalogue.text(reason)).param("a",jwt.getSubject()).param("t",transfer).update();
  return id;
 }
 void entry(UUID movement,UUID branch,Variant variant,String bucket,long delta,BigDecimal cost){
  var after=jdbc.sql("UPDATE stock_balances SET quantity=quantity+:d WHERE branch_id=:b AND variant_id=:v AND bucket=:k AND quantity+:d>=0 RETURNING quantity").param("d",delta).param("b",branch).param("v",variant.id()).param("k",bucket).query(Long.class).optional();
  if(after.isEmpty())throw InventoryProblem.conflict("INSUFFICIENT_STOCK");
  jdbc.sql("INSERT INTO stock_entries(id,movement_id,branch_id,variant_id,bucket,delta,quantity_after,sku,label,unit_cost) VALUES(:id,:m,:b,:v,:k,:d,:q,:s,:l,:c)")
   .param("id",UUID.randomUUID()).param("m",movement).param("b",branch).param("v",variant.id()).param("k",bucket).param("d",delta).param("q",after.get()).param("s",variant.sku()).param("l",Catalogue.label(variant)).param("c",cost).update();
 }
}
