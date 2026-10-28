package ma.rh.gateway;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.*;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
@Configuration
public class GatewayRoutes {
 @Bean RouteLocator routes(RouteLocatorBuilder builder,@Value("${rh.services.administration}") String admin,@Value("${rh.services.inventory}") String inventory){
  return builder.routes()
   .route("administration",r->r.path("/api/v1/branches","/api/v1/branches/**","/api/v1/branch-directory").filters(f->f.tokenRelay().removeRequestHeader("Cookie").removeRequestHeader("X-User-Id").removeRequestHeader("X-Branch-Id")).uri(admin))
   .route("inventory",r->r.path("/api/v1/catalog/**","/api/v1/stock/**").filters(f->f.tokenRelay().removeRequestHeader("Cookie").removeRequestHeader("X-User-Id").removeRequestHeader("X-Branch-Id")).uri(inventory)).build();
 }
}