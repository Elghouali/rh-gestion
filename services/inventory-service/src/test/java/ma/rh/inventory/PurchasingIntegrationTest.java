package ma.rh.inventory;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static ma.rh.inventory.Models.*;
@SpringBootTest(classes=InventoryApplication.class) @AutoConfigureMockMvc @Testcontainers
class PurchasingIntegrationTest {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
 @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);}
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcClient jdbc;
 @MockitoBean JwtDecoder decoder; @MockitoBean SiteAccess sites; @MockitoBean SupplierAccess suppliers; @MockitoSpyBean Catalogue catalogue;
 final UUID depot=UUID.fromString("10000000-0000-0000-0000-000000000001"),shop=UUID.fromString("10000000-0000-0000-0000-000000000002"),supplier=UUID.fromString("20000000-0000-0000-0000-000000000001");
 @BeforeEach void setup(){
  when(sites.allowed(any())).thenAnswer(i->((Jwt)i.getArgument(0)).getSubject().equals("owner")?List.of(new Site(depot,"DEPOT","Dépôt","DEPOT"),new Site(shop,"M01","Shop","STORE")):List.of(new Site(depot,"DEPOT","Dépôt","DEPOT")));
  when(suppliers.get(any(),any())).thenReturn(new SupplierAccess.Supplier(supplier,"Supplier test","",true));
 }
 org.springframework.test.web.servlet.request.RequestPostProcessor user(String actor){String role=actor.equals("owner")?"OWNER":actor.equals("stock")?"STOCK_MANAGER":"CASHIER";return jwt().jwt(j->j.subject(actor).claim("realm_access",Map.of("roles",List.of(role)))).authorities(new SimpleGrantedAuthority("ROLE_"+role));}
 JsonNode postJson(String path,String actor,Object input,int code)throws Exception{return postKey(path,actor,input,UUID.randomUUID().toString(),code);}
 JsonNode postKey(String path,String actor,Object input,String key,int code)throws Exception{var r=mvc.perform(post(path).with(user(actor)).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input))).andExpect(status().is(code)).andReturn().getResponse();return r.getContentAsString().isBlank()?json.createObjectNode():json.readTree(r.getContentAsString());}
 JsonNode variant()throws Exception{
  var p=postJson("/api/v1/catalog/products","owner",Map.of("name","Purchase "+UUID.randomUUID(),"brand","RH","category","ACCESSORY"),201);
  return postJson("/api/v1/catalog/variants","owner",Map.of("productId",p.get("id").asText(),"sku","BUY-"+UUID.randomUUID(),"color","Noir","wholesalePrice","100","retailPrice","120"),201);
 }
 Map<String,Object> orderInput(JsonNode v,long qty){return Map.of("supplierId",supplier,"branchId",depot,"reference","TEST-"+UUID.randomUUID(),"note","Comptage réel requis","items",List.of(Map.of("variantId",v.get("id").asText(),"quantity",qty,"unitCost","70.1250")));}
 JsonNode order(JsonNode v,long qty)throws Exception{return postJson("/api/v1/purchases","owner",orderInput(v,qty),201);}
 Map<String,Object> receipt(JsonNode v,long qty){return Map.of("reference","BL-"+UUID.randomUUID(),"reason","Réception physique","items",List.of(Map.of("variantId",v.get("id").asText(),"quantity",qty)));}
 Map<String,Object> inspection(JsonNode v,long accepted,long rejected){return Map.of("reason","Contrôle physique","items",List.of(Map.of("variantId",v.get("id").asText(),"accepted",accepted,"rejected",rejected)));}
 long balance(JsonNode v,String bucket){return jdbc.sql("SELECT coalesce(sum(quantity),0) FROM stock_balances WHERE branch_id=:b AND variant_id=:v AND bucket=:k").param("b",depot).param("v",UUID.fromString(v.get("id").asText())).param("k",bucket).query(Long.class).single();}
 String path(JsonNode order){return "/api/v1/purchases/"+order.get("id").asText();}
 @Test void orderDoesNotCreateStockAndSnapshotsPrices()throws Exception{var v=variant();var o=order(v,5);assertThat(o.at("/items/0/unitCost").asText()).isEqualTo("70.125");assertThat(balance(v,"SELLABLE")).isZero();assertThat(o.get("supplierName").asText()).isEqualTo("Supplier test");}
 @Test void physicalReceiptRequiresInspectionBeforeAvailability()throws Exception{var v=variant();var o=order(v,5);var r=postJson(path(o)+"/receipts","stock",receipt(v,3),201);assertThat(balance(v,"SELLABLE")).isZero();assertThat(balance(v,"QUARANTINE")).isEqualTo(3);mvc.perform(get("/api/v1/stock/balances").with(user("stock")).param("q",v.get("sku").asText())).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quarantine").value(3));postJson(path(o)+"/receipts/"+r.get("id").asText()+"/inspections","stock",inspection(v,2,1),201);assertThat(balance(v,"SELLABLE")).isEqualTo(2);assertThat(balance(v,"QUARANTINE")).isZero();assertThat(balance(v,"REJECTED")).isEqualTo(1);}
 @Test void receiptReplayAndInspectionReplayNeverDuplicateStock()throws Exception{var v=variant();var o=order(v,4);var key=UUID.randomUUID().toString();var body=receipt(v,4);var r=postKey(path(o)+"/receipts","stock",body,key,201);assertThat(postKey(path(o)+"/receipts","stock",body,key,201)).isEqualTo(r);var ik=UUID.randomUUID().toString();var in=inspection(v,4,0);String p=path(o)+"/receipts/"+r.get("id").asText()+"/inspections";postKey(p,"stock",in,ik,201);postKey(p,"stock",in,ik,201);assertThat(balance(v,"SELLABLE")).isEqualTo(4);}
 @Test void cannotOverReceiveOrOverInspect()throws Exception{var v=variant();var o=order(v,2);postJson(path(o)+"/receipts","stock",receipt(v,3),409);var r=postJson(path(o)+"/receipts","stock",receipt(v,2),201);postJson(path(o)+"/receipts/"+r.get("id").asText()+"/inspections","stock",inspection(v,2,1),409);}
 @Test void cashierCannotReadOrMutatePurchases()throws Exception{var v=variant();postJson("/api/v1/purchases","cashier",orderInput(v,2),403);mvc.perform(get("/api/v1/purchases").with(user("cashier"))).andExpect(status().isForbidden());}
 @Test void stockManagerCannotCreateOrdersOrReceiveOutsideSite()throws Exception{var v=variant();postJson("/api/v1/purchases","stock",orderInput(v,2),403);var in=new HashMap<>(orderInput(v,2));in.put("branchId",shop);var o=postJson("/api/v1/purchases","owner",in,201);postJson(path(o)+"/receipts","stock",receipt(v,1),403);mvc.perform(get(path(o)).with(user("stock"))).andExpect(status().isForbidden());}
 @Test void managerNeverReceivesCostsInOrderOrReceiptJson()throws Exception{var v=variant();var o=order(v,2);String read=mvc.perform(get(path(o)).with(user("stock"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertThat(read).doesNotContain("unitCost");var r=postJson(path(o)+"/receipts","stock",receipt(v,1),201);assertThat(r.toString()).doesNotContain("unitCost");}
 @Test void closingRemainderBlocksNewReceiptsButAllowsPendingInspection()throws Exception{var v=variant();var o=order(v,5);var r=postJson(path(o)+"/receipts","stock",receipt(v,2),201);postJson(path(o)+"/close","owner",Map.of("reason","Solde fournisseur annulé"),201);postJson(path(o)+"/receipts","stock",receipt(v,1),409);postJson(path(o)+"/receipts/"+r.get("id").asText()+"/inspections","stock",inspection(v,2,0),201);assertThat(balance(v,"SELLABLE")).isEqualTo(2);}
 @Test void rejectsDuplicateLinesFractionalAndNegativeCosts()throws Exception{var v=variant();var base=new HashMap<>(orderInput(v,2));var item=Map.of("variantId",v.get("id").asText(),"quantity",1,"unitCost","70");base.put("items",List.of(item,item));postJson("/api/v1/purchases","owner",base,400);base.put("items",List.of(Map.of("variantId",v.get("id").asText(),"quantity",1.5,"unitCost","70")));postJson("/api/v1/purchases","owner",base,400);base.put("items",List.of(Map.of("variantId",v.get("id").asText(),"quantity",1,"unitCost","-1")));postJson("/api/v1/purchases","owner",base,400);}
 @Test void unknownSupplierFailsClosed()throws Exception{doThrow(new InventoryProblem(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"FINANCE_UNAVAILABLE")).when(suppliers).get(any(),any());postJson("/api/v1/purchases","owner",orderInput(variant(),2),503);}
 @Test void cannotInspectAnUnrelatedReceipt()throws Exception{var v=variant();var a=order(v,2);var b=order(v,2);var r=postJson(path(a)+"/receipts","stock",receipt(v,1),201);postJson(path(b)+"/receipts/"+r.get("id").asText()+"/inspections","stock",inspection(v,1,0),404);}
 @Test void concurrentReceiptOfLastUnitsHasSingleWinner()throws Exception{var v=variant();var o=order(v,2);var pool=Executors.newFixedThreadPool(2);try{Callable<Integer> action=()->mvc.perform(post(path(o)+"/receipts").with(user("stock")).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(receipt(v,2)))).andReturn().getResponse().getStatus();var a=pool.submit(action);var b=pool.submit(action);assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);assertThat(balance(v,"QUARANTINE")).isEqualTo(2);}finally{pool.shutdownNow();}}
 @Test void managerCannotReleaseQuarantineWithoutOwnerApproval()throws Exception{var v=variant();var o=order(v,2);var r=postJson(path(o)+"/receipts","stock",receipt(v,2),201);postJson(path(o)+"/receipts/"+r.get("id").asText()+"/inspections","stock",inspection(v,2,0),403);assertThat(balance(v,"SELLABLE")).isZero();}
 @Test void orderedVariantIdentityIsFrozenBeforeFirstReceipt()throws Exception{var v=variant();order(v,2);var body=Map.of("sku",v.get("sku").asText(),"color","Bleu","wholesalePrice","100","retailPrice","120","minimumStock",0,"active",true,"version",0);mvc.perform(put("/api/v1/catalog/variants/"+v.get("id").asText()).with(user("owner")).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDENTITY_LOCKED"));}
 @Test void purchaseUsesTransferCompatibleLexicographicVariantLockOrder()throws Exception{
  var a=variant();var b=variant();UUID low=UUID.fromString("10000000-0000-0000-0000-000000000001"),high=UUID.fromString("f0000000-0000-0000-0000-000000000001");
  jdbc.sql("UPDATE product_variants SET id=:new WHERE id=:old").param("new",low).param("old",UUID.fromString(a.get("id").asText())).update();jdbc.sql("UPDATE product_variants SET id=:new WHERE id=:old").param("new",high).param("old",UUID.fromString(b.get("id").asText())).update();clearInvocations(catalogue);
  var in=Map.of("supplierId",supplier,"branchId",depot,"reference","LOCK-"+UUID.randomUUID(),"note","Ordering test","items",List.of(Map.of("variantId",high,"quantity",1,"unitCost","70"),Map.of("variantId",low,"quantity",1,"unitCost","70")));
  postJson("/api/v1/purchases","owner",in,201);var ordered=inOrder(catalogue);ordered.verify(catalogue).lock(low);ordered.verify(catalogue).lock(high);
 }
}