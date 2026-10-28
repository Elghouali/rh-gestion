package ma.rh.admin;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.*;
@Configuration
public class AdminSecurity {
 @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
  var converter=new JwtAuthenticationConverter();
  converter.setJwtGrantedAuthoritiesConverter(jwt->{
   Object realm=jwt.getClaim("realm_access");
   if(!(realm instanceof Map<?,?> map)||!(map.get("roles") instanceof Collection<?> roles))return List.of();
   return roles.stream().filter(String.class::isInstance).map(String.class::cast)
      .filter(Set.of("OWNER","CASHIER","STOCK_MANAGER")::contains)
      .<org.springframework.security.core.GrantedAuthority>map(role->new SimpleGrantedAuthority("ROLE_"+role)).toList();
  });
  return http.csrf(csrf->csrf.disable())
   .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(a->a.requestMatchers("/actuator/health","/actuator/health/**").permitAll()
    .requestMatchers("/api/v1/branches","/api/v1/branches/**").hasAnyRole("OWNER","CASHIER","STOCK_MANAGER")
    .anyRequest().denyAll())
   .oauth2ResourceServer(o->o.jwt(j->j.jwtAuthenticationConverter(converter))).build();
 }
}