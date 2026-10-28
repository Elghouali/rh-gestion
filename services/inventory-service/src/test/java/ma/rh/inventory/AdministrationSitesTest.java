package ma.rh.inventory;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.*;

class AdministrationSitesTest {
 HttpServer server;AdministrationSites sites;AtomicInteger status;AtomicReference<String> body,authorization,path;
 Jwt jwt=Jwt.withTokenValue("test-token").header("alg","RS256").subject("stock").build();
 @BeforeEach void start()throws Exception{
  status=new AtomicInteger(200);body=new AtomicReference<>("[{\"id\":\"10000000-0000-0000-0000-000000000001\",\"code\":\"DEPOT\",\"name\":\"Dépôt\",\"type\":\"DEPOT\"}]");authorization=new AtomicReference<>();path=new AtomicReference<>();
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",exchange->{authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));path.set(exchange.getRequestURI().getPath());byte[] bytes=body.get().getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status.get(),bytes.length);try(var output=exchange.getResponseBody()){output.write(bytes);}});
  server.start();sites=new AdministrationSites(RestClient.builder(),"http://127.0.0.1:"+server.getAddress().getPort());
 }
 @AfterEach void stop(){server.stop(0);}
 @Test void forwardsValidatedBearerTokenAndReadsOnlyAllowedSites(){assertThat(sites.allowed(jwt)).hasSize(1);assertThat(authorization.get()).isEqualTo("Bearer test-token");assertThat(path.get()).isEqualTo("/api/v1/branches");}
 @Test void usesSeparateDestinationDirectory(){assertThat(sites.directory(jwt)).hasSize(1);assertThat(path.get()).isEqualTo("/api/v1/branch-directory");}
 @Test void preservesAuthenticationFailures(){status.set(401);assertThat(catchThrowableOfType(()->sites.allowed(jwt),InventoryProblem.class).status).isEqualTo(HttpStatus.UNAUTHORIZED);}
 @Test void preservesAuthorizationFailures(){status.set(403);assertThat(catchThrowableOfType(()->sites.allowed(jwt),InventoryProblem.class).status).isEqualTo(HttpStatus.FORBIDDEN);}
 @Test void failsClosedOnServerFailure(){status.set(500);assertThat(catchThrowableOfType(()->sites.allowed(jwt),InventoryProblem.class).status).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);}
 @Test void refusesMalformedDirectory(){body.set("[{\"id\":null,\"code\":\"X\",\"name\":\"X\"}]");assertThat(catchThrowableOfType(()->sites.allowed(jwt),InventoryProblem.class).status).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);}
}