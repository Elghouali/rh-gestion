package ma.rh.gateway;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.*;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
@Configuration
public class GatewayRoutes {
 @Bean RouteLocator routes(RouteLocatorBuilder builder,@Value("${rh.services.administration}") String admin){
  return builder.routes().route("administration",r->r.path("/api/v1/branches","/api/v1/branches/**")
   .filters(f->f.tokenRelay().removeRequestHeader("Cookie").removeRequestHeader("X-User-Id").removeRequestHeader("X-Branch-Id"))
   .uri(admin)).build();
 }
}