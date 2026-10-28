package ma.rh.gateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.*;
@WebFluxTest(SessionController.class)
@Import(GatewaySecurity.class)
class GatewaySecurityTest {
 @Autowired WebTestClient client;
 @MockitoBean ReactiveClientRegistrationRepository registrations;
 @Test void anonymousSessionIsExplicit(){client.get().uri("/api/v1/session").exchange().expectStatus().isOk().expectBody().jsonPath("$.authenticated").isEqualTo(false);}
 @Test void apiDoesNotRedirectAnonymousToHtmlLogin(){client.get().uri("/api/v1/branches").exchange().expectStatus().isUnauthorized();}
 @Test void sessionNeverExposesTokens(){client.mutateWith(mockOidcLogin()).get().uri("/api/v1/session").exchange().expectStatus().isOk().expectBody().jsonPath("$.authenticated").isEqualTo(true).jsonPath("$.accessToken").doesNotExist();}
 @Test void csrfTokenCanBeObtainedBeforeLogin(){client.get().uri("/api/v1/csrf").exchange().expectStatus().isOk().expectBody().jsonPath("$.token").isNotEmpty();}
 @Test void logoutRequiresCsrf(){client.mutateWith(mockOidcLogin()).post().uri("/logout").exchange().expectStatus().isForbidden();}
}