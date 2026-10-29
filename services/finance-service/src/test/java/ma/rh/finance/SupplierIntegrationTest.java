package ma.rh.finance;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.fasterxml.jackson.databind.*;
@SpringBootTest(classes=FinanceApplication.class) @AutoConfigureMockMvc @Testcontainers
class SupplierIntegrationTest {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
 @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);}
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcClient jdbc; @MockitoBean JwtDecoder decoder;
 org.springframework.test.web.servlet.request.RequestPostProcessor user(String role){return jwt().jwt(j->j.subject(role.toLowerCase()).claim("realm_access",Map.of("roles",List.of(role)))).authorities(new SimpleGrantedAuthority("ROLE_"+role));}
 JsonNode create(String name,String key)throws Exception{return json.readTree(mvc.perform(post("/api/v1/suppliers").with(user("OWNER")).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("name",name,"phone","0600000000")))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());}
 @Test void ownerCreatesListsAndReadsSupplier()throws Exception{var s=create("Supplier "+UUID.randomUUID(),UUID.randomUUID().toString());mvc.perform(get("/api/v1/suppliers/"+s.get("id").asText()).with(user("OWNER"))).andExpect(status().isOk()).andExpect(jsonPath("$.phone").value("0600000000"));mvc.perform(get("/api/v1/suppliers").param("q",s.get("name").asText()).with(user("OWNER"))).andExpect(jsonPath("$.total").value(1));}
 @Test void creationIsIdempotent()throws Exception{String name="Supplier "+UUID.randomUUID(),key=UUID.randomUUID().toString();assertThat(create(name,key)).isEqualTo(create(name,key));}
 @Test void cashierAndStockManagerCannotAccessSupplierContacts()throws Exception{for(String role:List.of("CASHIER","STOCK_MANAGER"))mvc.perform(get("/api/v1/suppliers").with(user(role))).andExpect(status().isForbidden());}
 @Test void anonymousIsRejected()throws Exception{mvc.perform(get("/api/v1/suppliers")).andExpect(status().isUnauthorized());}
 @Test void normalizedDuplicateIsRejected()throws Exception{String name="Supplier "+UUID.randomUUID();create(name,UUID.randomUUID().toString());mvc.perform(post("/api/v1/suppliers").with(user("OWNER")).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("name","  "+name.toUpperCase()+"  ","phone","")))).andExpect(status().isConflict());}
 @Test void ownerArchivesAndStaleVersionCannotOverwrite()throws Exception{var s=create("Supplier "+UUID.randomUUID(),UUID.randomUUID().toString());String path="/api/v1/suppliers/"+s.get("id").asText();String body=json.writeValueAsString(Map.of("name",s.get("name").asText(),"phone","","active",false,"version",0));mvc.perform(put(path).with(user("OWNER")).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));mvc.perform(put(path).with(user("OWNER")).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());}
 @Test void inputIsValidated()throws Exception{mvc.perform(post("/api/v1/suppliers").with(user("OWNER")).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"phone\":\"\"}")).andExpect(status().isBadRequest());}
}
