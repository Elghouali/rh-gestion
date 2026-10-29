package ma.rh.inventory;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.*;
class FinanceSuppliersTest {
 HttpServer server;FinanceSuppliers suppliers;AtomicInteger status;AtomicReference<String> body,authorization,path;
 final UUID id=UUID.fromString("20000000-0000-0000-0000-000000000001");
 Jwt jwt=Jwt.withTokenValue("test-token").header("alg","RS256").subject("owner").build();
 @BeforeEach void start()throws Exception{
  status=new AtomicInteger(200);body=new AtomicReference<>("{\"id\":\""+id+"\",\"name\":\"Test Supplier\",\"phone\":\"\",\"active\":true}");authorization=new AtomicReference<>();path=new AtomicReference<>();
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",exchange->{authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));path.set(exchange.getRequestURI().getPath());byte[] bytes=body.get().getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status.get(),bytes.length);try(var output=exchange.getResponseBody()){output.write(bytes);}});
  server.start();suppliers=new FinanceSuppliers(RestClient.builder(),"http://127.0.0.1:"+server.getAddress().getPort());
 }
 @AfterEach void stop(){server.stop(0);}
 @Test void forwardsBearerAndChecksReturnedId(){assertThat(suppliers.get(jwt,id).id()).isEqualTo(id);assertThat(authorization.get()).isEqualTo("Bearer test-token");assertThat(path.get()).isEqualTo("/api/v1/suppliers/"+id);}
 @Test void preservesAuthenticationFailure(){status.set(401);assertThat(catchThrowableOfType(()->suppliers.get(jwt,id),InventoryProblem.class).status).isEqualTo(HttpStatus.UNAUTHORIZED);}
 @Test void preservesAuthorizationFailure(){status.set(403);assertThat(catchThrowableOfType(()->suppliers.get(jwt,id),InventoryProblem.class).status).isEqualTo(HttpStatus.FORBIDDEN);}
 @Test void mapsMissingSupplier(){status.set(404);assertThat(catchThrowableOfType(()->suppliers.get(jwt,id),InventoryProblem.class).code).isEqualTo("SUPPLIER_NOT_FOUND");}
 @Test void failsClosedOnServerFailure(){status.set(500);assertThat(catchThrowableOfType(()->suppliers.get(jwt,id),InventoryProblem.class).status).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);}
 @Test void rejectsWrongSupplierId(){body.set("{\"id\":\""+UUID.randomUUID()+"\",\"name\":\"Wrong\",\"phone\":\"\",\"active\":true}");assertThat(catchThrowableOfType(()->suppliers.get(jwt,id),InventoryProblem.class).status).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);}
 @Test void rejectsMalformedResponse(){body.set("not-json");assertThat(catchThrowableOfType(()->suppliers.get(jwt,id),InventoryProblem.class).status).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);}
}
