package ma.rh.inventory;

import static ma.rh.inventory.Models.*;
import java.text.Normalizer;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;

@Service
public class Catalogue {
 static final String VARIANTS="SELECT v.id,v.product_id,p.name AS product_name,p.brand,p.category,v.sku,v.color,v.ram_gb,v.storage_gb,v.wholesale_price,v.retail_price,v.minimum_stock,(v.active AND p.active) AS active,v.version FROM product_variants v JOIN products p ON p.id=v.product_id";
 private final JdbcClient jdbc;private final Access access;private final Commands commands;
 public Catalogue(JdbcClient jdbc,Access access,Commands commands){this.jdbc=jdbc;this.access=access;this.commands=commands;}
 static String text(String value){return Normalizer.normalize(value.strip(),Normalizer.Form.NFKC).replaceAll("\\s+"," ");}
 static String sku(String value){String result=text(value).toUpperCase(Locale.ROOT);if(!result.matches("[A-Z0-9][A-Z0-9._/-]{0,63}"))throw InventoryProblem.invalid("INVALID_SKU");return result;}
 static int size(int size){if(size<1||size>100)throw InventoryProblem.invalid("INVALID_PAGE");return size;}
 static int page(int page){if(page<0||page>1000000)throw InventoryProblem.invalid("INVALID_PAGE");return page;}
 static String search(String q){if(q.length()>100)throw InventoryProblem.invalid("SEARCH_TOO_LONG");return "%"+text(q).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";}
 public Page<Product> products(Jwt jwt,String q,int page,int size){
  access.allowed(jwt);page(page);size(size);String query=search(q);
  var items=jdbc.sql("SELECT * FROM products WHERE name ILIKE :q OR brand ILIKE :q ORDER BY name,id LIMIT :n OFFSET :o").param("q",query).param("n",size).param("o",(long)page*size).query(Product.class).list();
  long total=jdbc.sql("SELECT count(*) FROM products WHERE name ILIKE :q OR brand ILIKE :q").param("q",query).query(Long.class).single();
  return new Page<>(items,page,size,total);
 }
 public Page<Variant> variants(Jwt jwt,String q,int page,int size){access.allowed(jwt);return variants(q,page,size);}
 Page<Variant> variants(String q,int page,int size){
  page(page);size(size);String query=search(q);
  String filter=" WHERE v.sku ILIKE :q OR v.color ILIKE :q OR p.name ILIKE :q OR p.brand ILIKE :q";
  var items=jdbc.sql(VARIANTS+filter+" ORDER BY p.name,v.sku,v.id LIMIT :n OFFSET :o").param("q",query).param("n",size).param("o",(long)page*size).query(Variant.class).list();
  long total=jdbc.sql("SELECT count(*) FROM product_variants v JOIN products p ON p.id=v.product_id"+filter).param("q",query).query(Long.class).single();
  return new Page<>(items,page,size,total);
 }
 Product product(UUID id){return jdbc.sql("SELECT * FROM products WHERE id=:id").param("id",id).query(Product.class).optional().orElseThrow(()->new InventoryProblem(HttpStatus.NOT_FOUND,"PRODUCT_NOT_FOUND"));}
 Variant variant(UUID id){return jdbc.sql(VARIANTS+" WHERE v.id=:id").param("id",id).query(Variant.class).optional().orElseThrow(()->new InventoryProblem(HttpStatus.NOT_FOUND,"VARIANT_NOT_FOUND"));}
 Variant lock(UUID id){
  if(jdbc.sql("SELECT id FROM product_variants WHERE id=:id FOR UPDATE").param("id",id).query(UUID.class).optional().isEmpty())throw new InventoryProblem(HttpStatus.NOT_FOUND,"VARIANT_NOT_FOUND");
  return variant(id);
 }
 public Product create(Jwt jwt,String key,ProductInput input){
  access.requireOwner(jwt);
  return commands.execute(jwt.getSubject(),key,"catalogue.product.create",input,Product.class,()->{
   UUID id=UUID.randomUUID();
   jdbc.sql("INSERT INTO products(id,name,brand,category) VALUES(:id,:n,:b,:c)").param("id",id).param("n",text(input.name())).param("b",text(input.brand())).param("c",input.category()).update();
   Product result=product(id);audit(id,"PRODUCT_CREATED",jwt,result);return result;
  });
 }
 public Product update(Jwt jwt,String key,UUID id,ProductUpdate input){
  access.requireOwner(jwt);
  return commands.execute(jwt.getSubject(),key,"catalogue.product.update:"+id,input,Product.class,()->{
   int changed=jdbc.sql("UPDATE products SET name=:n,brand=:b,active=:a,version=version+1 WHERE id=:id AND version=:v").param("n",text(input.name())).param("b",text(input.brand())).param("a",input.active()).param("id",id).param("v",input.version()).update();
   if(changed!=1)throw InventoryProblem.conflict("STALE_VERSION");
   Product result=product(id);audit(id,"PRODUCT_UPDATED",jwt,result);return result;
  });
 }
 public Variant create(Jwt jwt,String key,VariantInput input){
  access.requireOwner(jwt);
  return commands.execute(jwt.getSubject(),key,"catalogue.variant.create",input,Variant.class,()->{
   Product product=product(input.productId());if(!product.active())throw InventoryProblem.conflict("PRODUCT_ARCHIVED");
   requirePhoneAttributes(product.category(),input.ramGb(),input.storageGb());
   UUID id=UUID.randomUUID();
   jdbc.sql("INSERT INTO product_variants(id,product_id,sku,color,ram_gb,storage_gb,wholesale_price,retail_price,minimum_stock) VALUES(:id,:p,:s,:c,:r,:t,:w,:d,:m)")
    .param("id",id).param("p",input.productId()).param("s",sku(input.sku())).param("c",text(input.color())).param("r",input.ramGb()).param("t",input.storageGb()).param("w",input.wholesalePrice()).param("d",input.retailPrice()).param("m",input.minimumStock()).update();
   Variant result=variant(id);audit(id,"VARIANT_CREATED",jwt,result);return result;
  });
 }
 public Variant update(Jwt jwt,String key,UUID id,VariantUpdate input){
  access.requireOwner(jwt);
  return commands.execute(jwt.getSubject(),key,"catalogue.variant.update:"+id,input,Variant.class,()->{
   Variant old=lock(id);if(old.version()!=input.version())throw InventoryProblem.conflict("STALE_VERSION");
   requirePhoneAttributes(old.category(),input.ramGb(),input.storageGb());
   String sku=sku(input.sku()),color=text(input.color());
   boolean identityChanged=!old.sku().equals(sku)||!old.color().equalsIgnoreCase(color)||!Objects.equals(old.ramGb(),input.ramGb())||!Objects.equals(old.storageGb(),input.storageGb());
   if(identityChanged&&jdbc.sql("SELECT EXISTS(SELECT 1 FROM stock_entries WHERE variant_id=:id)").param("id",id).query(Boolean.class).single())throw InventoryProblem.conflict("IDENTITY_LOCKED");
   int changed=jdbc.sql("UPDATE product_variants SET sku=:s,color=:c,ram_gb=:r,storage_gb=:t,wholesale_price=:w,retail_price=:d,minimum_stock=:m,active=:a,version=version+1 WHERE id=:id AND version=:v")
    .param("s",sku).param("c",color).param("r",input.ramGb()).param("t",input.storageGb()).param("w",input.wholesalePrice()).param("d",input.retailPrice()).param("m",input.minimumStock()).param("a",input.active()).param("id",id).param("v",input.version()).update();
   if(changed!=1)throw InventoryProblem.conflict("STALE_VERSION");
   Variant result=variant(id);audit(id,"VARIANT_UPDATED",jwt,result);return result;
  });
 }
 private void requirePhoneAttributes(String category,Integer ram,Integer storage){if(category.equals("PHONE")&&(ram==null||storage==null))throw InventoryProblem.invalid("PHONE_ATTRIBUTES_REQUIRED");}
 private void audit(UUID id,String action,Jwt jwt,Object payload){
  jdbc.sql("INSERT INTO catalogue_audit(id,aggregate_id,action,actor,payload) VALUES(:id,:aggregate,:action,:actor,CAST(:payload AS jsonb))").param("id",UUID.randomUUID()).param("aggregate",id).param("action",action).param("actor",jwt.getSubject()).param("payload",commands.encode(payload)).update();
 }
 static String label(Variant v){return v.brand()+" "+v.productName()+" · "+v.color()+(v.ramGb()==null?"":" · "+v.ramGb()+" GB RAM")+(v.storageGb()==null?"":" / "+v.storageGb()+" GB");}
}
