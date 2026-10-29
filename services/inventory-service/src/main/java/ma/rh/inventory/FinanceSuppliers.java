package ma.rh.inventory;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
@Component
public class FinanceSuppliers implements SupplierAccess {
 private final RestClient client;
 public FinanceSuppliers(RestClient.Builder builder,@Value("${rh.finance-url:http://localhost:8083}") String url){
  var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());factory.setReadTimeout(Duration.ofSeconds(3));client=builder.baseUrl(url).requestFactory(factory).build();
 }
 public Supplier get(Jwt jwt,UUID id){try{
  var s=client.get().uri("/api/v1/suppliers/{id}",id).headers(h->h.setBearerAuth(jwt.getTokenValue())).retrieve().body(Supplier.class);
  if(s==null||!id.equals(s.id())||s.name()==null)throw new InventoryProblem(HttpStatus.SERVICE_UNAVAILABLE,"FINANCE_UNAVAILABLE");
  return s;
 }catch(HttpClientErrorException.Unauthorized e){throw new InventoryProblem(HttpStatus.UNAUTHORIZED,"SESSION_EXPIRED");}
 catch(HttpClientErrorException.Forbidden e){throw InventoryProblem.forbidden();}
 catch(HttpClientErrorException.NotFound e){throw new InventoryProblem(HttpStatus.NOT_FOUND,"SUPPLIER_NOT_FOUND");}
 catch(RestClientException e){throw new InventoryProblem(HttpStatus.SERVICE_UNAVAILABLE,"FINANCE_UNAVAILABLE");}}
}
