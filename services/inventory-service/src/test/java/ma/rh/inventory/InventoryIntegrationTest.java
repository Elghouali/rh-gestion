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

@SpringBootTest(classes=InventoryApplication.class)
@AutoConfigureMockMvc
@Testcontainers
class InventoryIntegrationTest {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
 @DynamicPropertySource static void props(DynamicPropertyRegistry r){
  r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);
 }
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcClient jdbc;
 @MockitoBean JwtDecoder decoder; @MockitoBean SiteAccess sites;
 final UUID depot=UUID.fromString("10000000-0000-0000-0000-000000000001"),shop=UUID.fromString("10000000-0000-0000-0000-000000000002"),other=UUID.fromString("10000000-0000-0000-0000-000000000003");
 final String owner="owner",stock="stock",cashier="cashier";
 @BeforeEach void setup(){
  when(sites.allowed(any())).thenAnswer(i->{Jwt j=i.getArgument(0);return j.getSubject().equals(owner)?List.of(new Site(depot,"DEPOT","Dépôt","DEPOT"),new Site(shop,"M01","Magasin 1","STORE"),new Site(other,"M02","Magasin 2","STORE")):j.getSubject().equals(stock)?List.of(new Site(depot,"DEPOT","Dépôt","DEPOT")):List.of(new Site(shop,"M01","Magasin 1","STORE"));});
  when(sites.directory(any())).thenReturn(List.of(new Site(depot,"DEPOT","Dépôt","DEPOT"),new Site(shop,"M01","Magasin 1","STORE"),new Site(other,"M02","Magasin 2","STORE")));
 }
 org.springframework.test.web.servlet.request.RequestPostProcessor user(String name){
  String role=name.equals(owner)?"OWNER":name.equals(stock)?"STOCK_MANAGER":"CASHIER";
  return jwt().jwt(j->j.subject(name).claim("realm_access",Map.of("roles",List.of(role)))).authorities(new SimpleGrantedAuthority("ROLE_"+role));
 }
 JsonNode postJson(String path,String actor,Object data,int status)throws Exception{return command(path,actor,data,UUID.randomUUID().toString(),status);}
 JsonNode command(String path,String actor,Object data,String key,int status)throws Exception{
  var response=mvc.perform(post(path).with(user(actor)).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data))).andExpect(status().is(status)).andReturn().getResponse();
  return response.getContentAsString().isBlank()?json.createObjectNode():json.readTree(response.getContentAsString());
 }
 JsonNode product()throws Exception{return postJson("/api/v1/catalog/products",owner,Map.of("name","Phone "+UUID.randomUUID(),"brand","RH Test","category","PHONE"),201);}
 JsonNode variant()throws Exception{
  var p=product();return postJson("/api/v1/catalog/variants",owner,Map.of("productId",p.get("id").asText(),"sku","SKU-"+UUID.randomUUID(),"color","Noir","ramGb",8,"storageGb",256,"wholesalePrice","100.00","retailPrice","120.00","minimumStock",2),201);
 }
 Map<String,Object> opening(JsonNode v,long qty){var m=new HashMap<String,Object>();m.put("branchId",depot);m.put("variantId",v.get("id").asText());m.put("quantity",qty);m.put("reason","Comptage initial vérifié");m.put("unitCost","70.00");return m;}
 JsonNode transfer(JsonNode v,long qty)throws Exception{return postJson("/api/v1/stock/transfers",stock,Map.of("sourceId",depot,"destinationId",shop,"reason","Réapprovisionnement","items",List.of(Map.of("variantId",v.get("id").asText(),"quantity",qty))),201);}
 Map<String,Object> receipt(JsonNode v,long qty){return Map.of("reason","Réception comptée","items",List.of(Map.of("variantId",v.get("id").asText(),"quantity",qty)));}
 long balance(UUID site,JsonNode v,String bucket){return jdbc.sql("SELECT COALESCE(SUM(quantity),0) FROM stock_balances WHERE branch_id=:b AND variant_id=:v AND bucket=:k").param("b",site).param("v",UUID.fromString(v.get("id").asText())).param("k",bucket).query(Long.class).single();}
 @Test void anonymousIsRejected()throws Exception{mvc.perform(get("/api/v1/catalog/variants")).andExpect(status().isUnauthorized());}
 @Test void cashierCannotCreateProduct()throws Exception{postJson("/api/v1/catalog/products",cashier,Map.of("name","X","brand","RH","category","PHONE"),403);}
 @Test void createsDistinctPhoneVariants()throws Exception{
  var p=product();var payload=new HashMap<String,Object>(Map.of("productId",p.get("id").asText(),"sku","X-"+UUID.randomUUID(),"color","Noir","ramGb",8,"storageGb",128,"wholesalePrice","100.00","retailPrice","120.00","minimumStock",0));
  var first=postJson("/api/v1/catalog/variants",owner,payload,201);payload.put("sku","Y-"+UUID.randomUUID());payload.put("storageGb",256);
  var second=postJson("/api/v1/catalog/variants",owner,payload,201);assertThat(first.get("id")).isNotEqualTo(second.get("id"));
 }
 @Test void rejectsNormalizedDuplicateVariant()throws Exception{
  var v=variant();postJson("/api/v1/catalog/variants",owner,Map.of("productId",v.get("productId").asText(),"sku","OTHER-"+UUID.randomUUID(),"color","  NOIR  ","ramGb",8,"storageGb",256,"wholesalePrice","10","retailPrice","20","minimumStock",0),409);
 }
 @Test void accessoriesSupportNullRamAndStorageWithoutDuplicate()throws Exception{
  var p=postJson("/api/v1/catalog/products",owner,Map.of("name","Charger "+UUID.randomUUID(),"brand","RH","category","ACCESSORY"),201);
  var payload=Map.of("productId",p.get("id").asText(),"sku","ACC-"+UUID.randomUUID(),"color","Blanc","wholesalePrice","10","retailPrice","20","minimumStock",0);
  postJson("/api/v1/catalog/variants",owner,payload,201);
  var duplicate=new HashMap<String,Object>(payload);duplicate.put("sku","ACC2-"+UUID.randomUUID());postJson("/api/v1/catalog/variants",owner,duplicate,409);
 }
 @Test void phoneRequiresRamAndStorage()throws Exception{
  var p=product();postJson("/api/v1/catalog/variants",owner,Map.of("productId",p.get("id").asText(),"sku","BAD-"+UUID.randomUUID(),"color","Noir","wholesalePrice","10","retailPrice","20","minimumStock",0),400);
 }
 @Test void negativePriceIsRejected()throws Exception{
  var p=product();postJson("/api/v1/catalog/variants",owner,Map.of("productId",p.get("id").asText(),"sku","BAD-"+UUID.randomUUID(),"color","Noir","ramGb",8,"storageGb",128,"wholesalePrice","-1","retailPrice","20","minimumStock",0),400);
 }
 @Test void openingIsIdempotentAndPostedOnce()throws Exception{
  var v=variant();var key=UUID.randomUUID().toString();var first=command("/api/v1/stock/openings",owner,opening(v,10),key,201);var second=command("/api/v1/stock/openings",owner,opening(v,10),key,201);
  assertThat(first).isEqualTo(second);assertThat(balance(depot,v,"SELLABLE")).isEqualTo(10);
 }
 @Test void idempotencyKeyCannotBeReusedWithChangedPayload()throws Exception{
  var v=variant();var key=UUID.randomUUID().toString();command("/api/v1/stock/openings",owner,opening(v,10),key,201);command("/api/v1/stock/openings",owner,opening(v,20),key,409);
 }
 @Test void zeroOpeningIsConfirmedAndCannotRepeat()throws Exception{var v=variant();postJson("/api/v1/stock/openings",owner,opening(v,0),201);postJson("/api/v1/stock/openings",owner,opening(v,0),409);}
 @Test void rejectsOpeningAfterAnAdjustment()throws Exception{
  var v=variant();postJson("/api/v1/stock/adjustments",owner,Map.of("branchId",depot,"variantId",v.get("id").asText(),"delta",1,"reason","Écart vérifié"),201);postJson("/api/v1/stock/openings",owner,opening(v,2),409);
 }
 @Test void preventsNegativeStock()throws Exception{
  var v=variant();postJson("/api/v1/stock/openings",owner,opening(v,2),201);
  postJson("/api/v1/stock/adjustments",owner,Map.of("branchId",depot,"variantId",v.get("id").asText(),"delta",-3,"reason","Perte"),409);assertThat(balance(depot,v,"SELLABLE")).isEqualTo(2);
 }
 @Test void transferMovesThroughTransitAndSupportsPartialReceipts()throws Exception{
  var v=variant();postJson("/api/v1/stock/openings",owner,opening(v,10),201);var t=transfer(v,4);
  assertThat(balance(depot,v,"SELLABLE")).isEqualTo(6);assertThat(balance(depot,v,"TRANSIT")).isEqualTo(4);assertThat(balance(shop,v,"SELLABLE")).isZero();
  postJson("/api/v1/stock/transfers/"+t.get("id").asText()+"/receipts",owner,receipt(v,2),201);
  assertThat(balance(shop,v,"SELLABLE")).isEqualTo(2);assertThat(balance(depot,v,"TRANSIT")).isEqualTo(2);
 }
 @Test void sourceEmployeeCannotReceiveForDestination()throws Exception{
  var v=variant();postJson("/api/v1/stock/openings",owner,opening(v,4),201);var t=transfer(v,2);postJson("/api/v1/stock/transfers/"+t.get("id").asText()+"/receipts",stock,receipt(v,2),403);
 }
 @Test void cannotReceiveMoreThanDispatched()throws Exception{
  var v=variant();postJson("/api/v1/stock/openings",owner,opening(v,4),201);var t=transfer(v,2);postJson("/api/v1/stock/transfers/"+t.get("id").asText()+"/receipts",owner,receipt(v,3),409);
 }
 @Test void receiptReplayDoesNotDuplicateStock()throws Exception{
  var v=variant();postJson("/api/v1/stock/openings",owner,opening(v,4),201);var t=transfer(v,2);String path="/api/v1/stock/transfers/"+t.get("id").asText()+"/receipts",key=UUID.randomUUID().toString();
  command(path,owner,receipt(v,1),key,201);command(path,owner,receipt(v,1),key,201);assertThat(balance(shop,v,"SELLABLE")).isEqualTo(1);
 }
 @Test void ownerCanResolveTransitLoss()throws Exception{
  var v=variant();postJson("/api/v1/stock/openings",owner,opening(v,4),201);var t=transfer(v,2);postJson("/api/v1/stock/transfers/"+t.get("id").asText()+"/losses",owner,receipt(v,2),201);assertThat(balance(depot,v,"TRANSIT")).isZero();
 }
 @Test void cashierCannotReadOtherSiteStockOrHistory()throws Exception{
  mvc.perform(get("/api/v1/stock/balances").param("branchId",depot.toString()).with(user(cashier))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/stock/movements").param("branchId",depot.toString()).with(user(cashier))).andExpect(status().isForbidden());
 }
 @Test void cashierMovementResponseOmitsCost()throws Exception{
  var v=variant();var p=new HashMap<>(opening(v,2));p.put("branchId",shop);postJson("/api/v1/stock/openings",owner,p,201);
  var body=mvc.perform(get("/api/v1/stock/movements").param("branchId",shop.toString()).param("variantId",v.get("id").asText()).with(user(cashier))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertThat(body).doesNotContain("unitCost").doesNotContain("70.00");
 }
 @Test void failedAdminLookupFailsClosed()throws Exception{
  doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)).when(sites).allowed(any());
  mvc.perform(get("/api/v1/stock/balances").with(user(cashier))).andExpect(status().isServiceUnavailable());
 }
 @Test void concurrentTransfersCannotSpendSameLastUnit()throws Exception{
  var v=variant();postJson("/api/v1/stock/openings",owner,opening(v,1),201);
  var data=Map.of("sourceId",depot,"destinationId",shop,"reason","Concurrent","items",List.of(Map.of("variantId",v.get("id").asText(),"quantity",1)));
  try(var executor=Executors.newFixedThreadPool(2)){
   var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
   Callable<Integer> job=()->{ready.countDown();go.await();return mvc.perform(post("/api/v1/stock/transfers").with(user(stock)).header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data))).andReturn().getResponse().getStatus();};
   var a=executor.submit(job);var b=executor.submit(job);assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();go.countDown();
   assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
  }
  assertThat(balance(depot,v,"SELLABLE")).isZero();
 }
 @Test void catalogueUpdateRejectsStaleVersion()throws Exception{
  var v=variant();var update=new HashMap<String,Object>(Map.of("sku",v.get("sku").asText(),"color","Noir","ramGb",8,"storageGb",256,"wholesalePrice","105","retailPrice","125","minimumStock",3,"active",true,"version",0));
  String path="/api/v1/catalog/variants/"+v.get("id").asText();
  mvc.perform(put(path).with(user(owner)).header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update))).andExpect(status().isOk());
  mvc.perform(put(path).with(user(owner)).header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update))).andExpect(status().isConflict());
 }
 @Test void journalCannotBeEditedOrDeleted()throws Exception{
  var v=variant();postJson("/api/v1/stock/openings",owner,opening(v,1),201);
  assertThatThrownBy(()->jdbc.sql("DELETE FROM stock_entries WHERE variant_id=:v").param("v",UUID.fromString(v.get("id").asText())).update()).isInstanceOf(Exception.class);
 }
 @Test void rejectsNullTransferLine()throws Exception{
  postJson("/api/v1/stock/transfers",stock,Map.of("sourceId",depot,"destinationId",shop,"reason","Bad","items",Collections.singletonList(null)),400);
 }
 @Test void rejectsFractionalStockQuantity()throws Exception{
  var v=variant();var data=new HashMap<>(opening(v,2));data.put("quantity",1.5);postJson("/api/v1/stock/openings",owner,data,400);
 }
 @Test void cashierCannotReceiveEvenAtAssignedShop()throws Exception{
  var v=variant();postJson("/api/v1/stock/openings",owner,opening(v,3),201);var t=transfer(v,1);postJson("/api/v1/stock/transfers/"+t.get("id").asText()+"/receipts",cashier,receipt(v,1),403);
 } @Test void searchableCatalogueAndScopedBalancesExposeConfirmedAndUnknownCounts()throws Exception{
  var v=variant();String sku=v.get("sku").asText();
  mvc.perform(get("/api/v1/catalog/products").param("q","RH Test").with(user(cashier))).andExpect(status().isOk()).andExpect(jsonPath("$.total").isNumber());
  mvc.perform(get("/api/v1/catalog/variants").param("q",sku).with(user(cashier))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].sku").value(sku));
  mvc.perform(get("/api/v1/stock/balances").param("q",sku).param("branchId",depot.toString()).with(user(owner))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].initialized").value(false));
  postJson("/api/v1/stock/openings",owner,opening(v,1),201);
  mvc.perform(get("/api/v1/stock/balances").param("q",sku).param("branchId",depot.toString()).with(user(owner))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(1)).andExpect(jsonPath("$.items[0].lowStock").value(true));
 }
 @Test void transferListAndDirectLookupRespectDestinationScope()throws Exception{
  var v=variant();postJson("/api/v1/stock/openings",owner,opening(v,4),201);var t=transfer(v,2);
  mvc.perform(get("/api/v1/stock/transfers").with(user(cashier))).andExpect(status().isOk()).andExpect(jsonPath("$.total").isNumber());
  mvc.perform(get("/api/v1/stock/transfers/"+t.get("id").asText()).with(user(cashier))).andExpect(status().isOk());
  var hidden=postJson("/api/v1/stock/transfers",stock,Map.of("sourceId",depot,"destinationId",other,"reason","Another shop","items",List.of(Map.of("variantId",v.get("id").asText(),"quantity",1))),201);
  mvc.perform(get("/api/v1/stock/transfers/"+hidden.get("id").asText()).with(user(cashier))).andExpect(status().isForbidden());
 }
 @Test void canArchiveAndReceiveExistingTransitWithoutRewritingHistory()throws Exception{
  var v=variant();postJson("/api/v1/stock/openings",owner,opening(v,4),201);var t=transfer(v,2);
  var update=Map.of("sku",v.get("sku").asText(),"color","Noir","ramGb",8,"storageGb",256,"wholesalePrice","100","retailPrice","120","minimumStock",2,"active",false,"version",0);
  mvc.perform(put("/api/v1/catalog/variants/"+v.get("id").asText()).with(user(owner)).header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update))).andExpect(status().isOk());
  postJson("/api/v1/stock/transfers/"+t.get("id").asText()+"/receipts",owner,receipt(v,2),201);assertThat(balance(shop,v,"SELLABLE")).isEqualTo(2);
 }
 @Test void usedVariantIdentityCannotChange()throws Exception{
  var v=variant();postJson("/api/v1/stock/openings",owner,opening(v,1),201);
  var update=Map.of("sku",v.get("sku").asText(),"color","Blanc","ramGb",8,"storageGb",256,"wholesalePrice","100","retailPrice","120","minimumStock",2,"active",true,"version",0);
  mvc.perform(put("/api/v1/catalog/variants/"+v.get("id").asText()).with(user(owner)).header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update))).andExpect(status().isConflict());
 }
 @Test void modelEditIsVersionedAndArchivingPreservesVariants()throws Exception{
  var v=variant();var update=Map.of("name","Renamed "+UUID.randomUUID(),"brand","RH Test","active",false,"version",0);
  mvc.perform(put("/api/v1/catalog/products/"+v.get("productId").asText()).with(user(owner)).header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update))).andExpect(status().isOk());
  mvc.perform(get("/api/v1/catalog/variants").param("q",v.get("sku").asText()).with(user(owner))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].active").value(false));
 }
 @Test void multiLineTransferRollsBackEveryLineOnInsufficientStock()throws Exception{
  var a=variant();var b=variant();postJson("/api/v1/stock/openings",owner,opening(a,3),201);
  long entries=jdbc.sql("SELECT count(*) FROM stock_entries").query(Long.class).single();
  postJson("/api/v1/stock/transfers",stock,Map.of("sourceId",depot,"destinationId",shop,"reason","Batch","items",List.of(Map.of("variantId",a.get("id").asText(),"quantity",2),Map.of("variantId",b.get("id").asText(),"quantity",1))),409);
  assertThat(balance(depot,a,"SELLABLE")).isEqualTo(3);assertThat(jdbc.sql("SELECT count(*) FROM stock_entries").query(Long.class).single()).isEqualTo(entries);
 }
 @Test void twoReceiptsCannotConsumeSameRemainder()throws Exception{
  var v=variant();postJson("/api/v1/stock/openings",owner,opening(v,2),201);var t=transfer(v,2);
  try(var executor=Executors.newFixedThreadPool(2)){
   Callable<Integer> receive=()->mvc.perform(post("/api/v1/stock/transfers/"+t.get("id").asText()+"/receipts").with(user(owner)).header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(receipt(v,2)))).andReturn().getResponse().getStatus();
   var a=executor.submit(receive);var b=executor.submit(receive);assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
  }
  assertThat(balance(shop,v,"SELLABLE")).isEqualTo(2);assertThat(balance(depot,v,"TRANSIT")).isZero();
 }}
