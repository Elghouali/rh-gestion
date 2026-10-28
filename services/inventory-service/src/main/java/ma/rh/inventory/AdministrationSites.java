package ma.rh.inventory;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import ma.rh.inventory.Models.Site;

@Component
public class AdministrationSites implements SiteAccess {
 private final RestClient client;
 public AdministrationSites(RestClient.Builder builder,@Value("${rh.administration-url}") String url){
  var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
  factory.setReadTimeout(Duration.ofSeconds(3));
  client=builder.baseUrl(url).requestFactory(factory).build();
 }
 public List<Site> allowed(Jwt jwt){return read("/api/v1/branches",jwt);}
 public List<Site> directory(Jwt jwt){return read("/api/v1/branch-directory",jwt);}
 private List<Site> read(String path,Jwt jwt){
  try {
   var body=client.get().uri(path).headers(h->h.setBearerAuth(jwt.getTokenValue())).retrieve().body(Site[].class);
   if(body==null||Arrays.stream(body).anyMatch(s->s.id()==null||s.name()==null||s.code()==null))throw new InventoryProblem(HttpStatus.SERVICE_UNAVAILABLE,"ADMIN_UNAVAILABLE");
   return List.of(body);
  }catch(HttpClientErrorException.Unauthorized failure){throw new InventoryProblem(HttpStatus.UNAUTHORIZED,"SESSION_EXPIRED");}
   catch(HttpClientErrorException.Forbidden failure){throw InventoryProblem.forbidden();}
   catch(RestClientException failure){throw new InventoryProblem(HttpStatus.SERVICE_UNAVAILABLE,"ADMIN_UNAVAILABLE");}
 }
}
