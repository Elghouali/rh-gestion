package ma.rh.gateway;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Map;
@RestController
public class SessionController {
 public record SessionInfo(boolean authenticated,String displayName,List<String> roles){}
 public record CsrfInfo(String parameterName,String headerName,String token){}
 @GetMapping("/api/v1/session")
 public Mono<SessionInfo> session(Mono<OAuth2AuthenticationToken> authentication){
  return authentication.map(a->{
   var user=(OidcUser)a.getPrincipal();
   String name=user.getFullName()!=null?user.getFullName():user.getSubject();
   Object realm=user.getClaim("realm_access");
   List<String> roles=List.of();
   if(realm instanceof Map<?,?> map && map.get("roles") instanceof List<?> values){
    roles=values.stream().filter(String.class::isInstance).map(String.class::cast).filter(List.of("OWNER","CASHIER","STOCK_MANAGER")::contains).toList();
   }
   return new SessionInfo(true,name,roles);
  }).defaultIfEmpty(new SessionInfo(false,"",List.of()));
 }
 @GetMapping("/api/v1/csrf")
 public Mono<CsrfInfo> csrf(ServerWebExchange exchange){
  Mono<CsrfToken> token=exchange.getAttribute(CsrfToken.class.getName());
  return token==null?Mono.error(new IllegalStateException("CSRF token unavailable")):
   token.map(t->new CsrfInfo(t.getParameterName(),t.getHeaderName(),t.getToken()));
 }
}