package ma.rh.admin;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(BranchController.class)
@Import({AdminSecurity.class,BranchQueryService.class})
class DirectoryApiTest {
 @Autowired MockMvc mvc;@MockitoBean BranchRepository repository;@MockitoBean JwtDecoder decoder;
 @Test void stockManagerCanReadDestinationDirectory()throws Exception{
  when(repository.findAll()).thenReturn(List.of(new Branch(UUID.randomUUID(),"M01","Magasin 1","STORE")));
  mvc.perform(get("/api/v1/branch-directory").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STOCK_MANAGER")))).andExpect(status().isOk()).andExpect(jsonPath("$[0].code").value("M01"));
 }
 @Test void cashierCannotReadUnassignedDirectory()throws Exception{
  mvc.perform(get("/api/v1/branch-directory").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CASHIER")))).andExpect(status().isForbidden());verifyNoInteractions(repository);
 }
}