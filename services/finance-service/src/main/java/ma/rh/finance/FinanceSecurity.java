package ma.rh.finance;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
public class FinanceSecurity {
 @Bean SecurityFilterChain security(HttpSecurity http)throws Exception{
  var converter=new JwtAuthenticationConverter();
  converter.setJwtGrantedAuthoritiesConverter(jwt->{
   Object realm=jwt.getClaim("realm_access");
   if(!(realm instanceof Map<?,?> map)||!(map.get("roles") instanceof Collection<?> roles))return List.of();
   return roles.stream().filter(String.class::isInstance).map(String.class::cast).filter(Set.of("OWNER","CASHIER","STOCK_MANAGER")::contains).<GrantedAuthority>map(r->new SimpleGrantedAuthority("ROLE_"+r)).toList();
  });
  return http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(a->a.requestMatchers("/actuator/health","/actuator/health/**").permitAll().requestMatchers("/api/v1/suppliers","/api/v1/suppliers/**").hasRole("OWNER").anyRequest().denyAll())
   .oauth2ResourceServer(o->o.jwt(j->j.jwtAuthenticationConverter(converter))).build();
 }
}
