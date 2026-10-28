package ma.rh.gateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import static org.mockito.Mockito.when;
@WebFluxTest(SessionController.class)
@Import(GatewaySecurity.class)
class PkceAuthorizationTest {
 @Autowired WebTestClient client;
 @MockitoBean ReactiveClientRegistrationRepository registrations;
 @Test void confidentialClientUsesS256Pkce(){
  var registration=new OidcRegistration().registrations("http://localhost:8180","http://keycloak:8080","local-test").findByRegistrationId("rh").block();
  when(registrations.findByRegistrationId("rh")).thenReturn(Mono.just(registration));
  client.get().uri("/oauth2/authorization/rh").exchange().expectStatus().is3xxRedirection()
   .expectHeader().valueMatches("Location",".*code_challenge=.*")
   .expectHeader().valueMatches("Location",".*code_challenge_method=S256.*");
 }
}