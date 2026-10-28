package ma.rh.gateway;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import java.util.Map;
@Configuration
public class OidcRegistration {
 @Bean ReactiveClientRegistrationRepository registrations(
  @Value("${rh.oidc.public-url}") String publicUrl,@Value("${rh.oidc.internal-url}") String internalUrl,
  @Value("${rh.oidc.client-secret}") String secret){
  String publicRealm=publicUrl+"/realms/rh";
  String internalRealm=internalUrl+"/realms/rh";
  var registration=ClientRegistration.withRegistrationId("rh")
   .clientId("rh-bff").clientSecret(secret).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
   .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
   .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}").scope("openid","profile")
   .authorizationUri(publicRealm+"/protocol/openid-connect/auth")
   .tokenUri(internalRealm+"/protocol/openid-connect/token")
   .jwkSetUri(internalRealm+"/protocol/openid-connect/certs")
   .userInfoUri(internalRealm+"/protocol/openid-connect/userinfo").userNameAttributeName("sub")
   .issuerUri(publicRealm)
   .providerConfigurationMetadata(Map.of("end_session_endpoint",publicRealm+"/protocol/openid-connect/logout"))
   .clientName("RH").build();
  return new InMemoryReactiveClientRegistrationRepository(registration);
 }
}