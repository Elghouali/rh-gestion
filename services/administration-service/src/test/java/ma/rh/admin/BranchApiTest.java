package ma.rh.admin;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(BranchController.class)
@Import(AdminSecurity.class)
class BranchApiTest {
 @Autowired MockMvc mvc;
 @MockitoBean BranchRepository repository;
 @MockitoBean JwtDecoder decoder;
 private final UUID depot=UUID.fromString("10000000-0000-0000-0000-000000000001");
 private final UUID shop=UUID.fromString("10000000-0000-0000-0000-000000000002");
 @Test void anonymousIsUnauthorized() throws Exception {mvc.perform(get("/api/v1/branches")).andExpect(status().isUnauthorized());verifyNoInteractions(repository);}
 @Test void ownerSeesAllSites() throws Exception {
  when(repository.findAll()).thenReturn(List.of(new Branch(depot,"DEPOT","Dépôt central","DEPOT"),new Branch(shop,"M01","Magasin 1","STORE")));
  mvc.perform(get("/api/v1/branches").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OWNER")))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
 }
 @Test void cashierSeesOnlyAssignedSites() throws Exception {
  when(repository.findAssigned("ibrahim")).thenReturn(List.of(new Branch(shop,"M01","Magasin 1","STORE")));
  mvc.perform(get("/api/v1/branches").with(jwt().jwt(j->j.subject("ibrahim")).authorities(new SimpleGrantedAuthority("ROLE_CASHIER")))).andExpect(status().isOk()).andExpect(jsonPath("$[0].code").value("M01"));
  verify(repository,never()).findAll();
 }
 @Test void directAccessToOtherShopIsForbidden() throws Exception {
  when(repository.findAssigned("stock")).thenReturn(List.of(new Branch(depot,"DEPOT","Dépôt central","DEPOT")));
  mvc.perform(get("/api/v1/branches/"+shop).with(jwt().jwt(j->j.subject("stock")).authorities(new SimpleGrantedAuthority("ROLE_STOCK_MANAGER")))).andExpect(status().isForbidden());
 }
 @Test void unknownRoleIsForbidden() throws Exception {
  mvc.perform(get("/api/v1/branches").with(jwt())).andExpect(status().isForbidden()); verifyNoInteractions(repository);
 }
}