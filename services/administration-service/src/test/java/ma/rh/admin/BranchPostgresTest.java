package ma.rh.admin;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BranchPostgresTest {
    private static final String ISSUER = "https://identity.rh.test/realms/rh";
    private static final RSAKey KEY;
    private static final HttpServer JWKS;
    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("rh-test").generate();
            JWKS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            JWKS.createContext("/jwks", exchange -> {
                byte[] body = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var out = exchange.getResponseBody()) { out.write(body); }
            });
            JWKS.start();
        } catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/dev");
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
    }
    @AfterAll static void closeKeysServer() { JWKS.stop(0); }
    @Autowired BranchRepository repository;
    @Autowired MockMvc mvc;

    @Test void migrationsCreateDepotAndFourShops() { assertThat(repository.findAll()).hasSize(5); }
    @Test void cashierAssignmentIsFilteredByOidcSubject() {
        assertThat(repository.findAssigned("20000000-0000-0000-0000-000000000002"))
                .extracting(Branch::code).containsExactly("M01");
    }
    @Test void unknownSubjectHasNoAccess() { assertThat(repository.findAssigned("' OR 1=1 --")).isEmpty(); }
    @Test void acceptsSignedTokenWithCorrectAudience() throws Exception {
        mvc.perform(get("/api/v1/branches").header("Authorization", "Bearer " + token(KEY, ISSUER, "rh-services")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }
    @Test void deniesSignedTokenForAnotherAudience() throws Exception {
        mvc.perform(get("/api/v1/branches").header("Authorization", "Bearer " + token(KEY, ISSUER, "another-client")))
                .andExpect(status().isUnauthorized());
    }
    @Test void deniesSignedTokenFromAnotherIssuer() throws Exception {
        mvc.perform(get("/api/v1/branches").header("Authorization", "Bearer " + token(KEY, "https://wrong.test", "rh-services")))
                .andExpect(status().isUnauthorized());
    }
    @Test void deniesInvalidSignature() throws Exception {
        var wrong = new RSAKeyGenerator(2048).keyID("rh-test").generate();
        mvc.perform(get("/api/v1/branches").header("Authorization", "Bearer " + token(wrong, ISSUER, "rh-services")))
                .andExpect(status().isUnauthorized());
    }
    private String token(RSAKey key, String issuer, String audience) {
        var claims = JwtClaimsSet.builder().issuer(issuer)
                .subject("20000000-0000-0000-0000-000000000002")
                .audience(List.of(audience)).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(120))
                .claim("realm_access", Map.of("roles", List.of("CASHIER"))).build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId("rh-test").build(), claims)).getTokenValue();
    }
}
