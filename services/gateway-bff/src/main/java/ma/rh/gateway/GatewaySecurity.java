package ma.rh.gateway;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.csrf.*;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
@Configuration
public class GatewaySecurity {
 @Bean SecurityWebFilterChain security(ServerHttpSecurity http,ReactiveClientRegistrationRepository registrations){
  var resolver=new DefaultServerOAuth2AuthorizationRequestResolver(registrations);
  resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
  var logout=new OidcClientInitiatedServerLogoutSuccessHandler(registrations);
  logout.setPostLogoutRedirectUri("{baseUrl}/");
  return http.authorizeExchange(a->a.pathMatchers("/api/v1/session","/api/v1/csrf","/actuator/health","/actuator/health/**","/oauth2/**","/login/**").permitAll().anyExchange().authenticated())
   .exceptionHandling(e->e.authenticationEntryPoint(new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)))
   .csrf(c->c.csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
    .csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler()))
   .oauth2Login(o->o.authorizationRequestResolver(resolver).authenticationSuccessHandler(new org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler("/")))
   .logout(l->l.logoutSuccessHandler(logout)).build();
 }
}