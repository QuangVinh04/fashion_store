package com.fashionstore.identity.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fashionstore.common.autoconfigure.CommonSecurityAutoConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Resource server của identity-service với Keycloak THẬT (import đúng realm-fashion-store.json), cấu hình issuer / audience /
 * JWKS giống production. SecurityConfigTest chỉ giả lập JwtDecoder; test này chứng minh chữ ký, iss, aud và token
 * client_credentials của service account hoạt động đầu-cuối.
 */
@Tag("external-infrastructure")
@WebMvcTest
@ContextConfiguration(classes = {SecurityConfig.class, KeycloakRealmIntegrationTest.ProbeController.class})
@ImportAutoConfiguration({CommonSecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class})
class KeycloakRealmIntegrationTest {

    static final String REALM = "fashion-store";
    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newHttpClient();

    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>("quay.io/keycloak/keycloak:26.7.4")
            .withExposedPorts(8080)
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of("../../platform/keycloak/realm-fashion-store.json").toAbsolutePath().normalize()),
                    "/opt/keycloak/data/import/realm-fashion-store.json")
            .withCommand("start-dev", "--import-realm")
            .waitingFor(Wait.forLogMessage(".*Listening on:.*", 1).withStartupTimeout(Duration.ofMinutes(3)));

    static String baseUrl;
    static String userToken;
    static String adminToken;
    static String wrongAudienceToken;
    static String serviceAccountToken;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtDecoder jwtDecoder;

    // Phải xong trước khi Spring nạp context (đọc property bên dưới) nên không dùng @BeforeAll
    static {
        KEYCLOAK.start();
        // start-dev không có KC_HOSTNAME: iss của token theo URL mà test dùng để gọi Keycloak
        baseUrl = "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
        try {
            issueTokens();
        } catch (Exception e) {
            KEYCLOAK.stop();
            throw new IllegalStateException("Cannot obtain tokens from the test Keycloak", e);
        }
    }

    @DynamicPropertySource
    static void resourceServer(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> baseUrl + "/realms/" + REALM);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> baseUrl + "/realms/" + REALM + "/protocol/openid-connect/certs");
        registry.add("spring.security.oauth2.resourceserver.jwt.audiences", () -> "fashion-api");
    }

    private static void issueTokens() throws Exception {
        String masterToken = tokenOf("master", Map.of(
                "grant_type", "password", "client_id", "admin-cli", "username", "admin", "password", "admin"));
        // Realm tắt direct grant cho mọi client; chỉ test mới bật để lấy token user không cần trình duyệt
        enableDirectGrants(masterToken, "swagger-ui");
        createPublicClientWithoutApiAudience(masterToken, "no-api-audience-client");

        userToken = passwordToken("swagger-ui", "customer@fashion.local", "Customer@12345");
        adminToken = passwordToken("swagger-ui", "admin@fashion.local", "Admin@12345");
        wrongAudienceToken = passwordToken("no-api-audience-client", "customer@fashion.local", "Customer@12345");
        serviceAccountToken = tokenOf(REALM, Map.of(
                "grant_type", "client_credentials", "client_id", "order-service", "client_secret", "dev-order-service-secret"));
    }

    @AfterAll
    static void stop() {
        KEYCLOAK.stop();
    }

    @Test
    void realUserTokenCarriesTheClaimsTheServicesRelyOn() {
        Jwt jwt = jwtDecoder.decode(userToken);

        assertThat(jwt.getSubject()).isNotBlank();
        assertThat(jwt.getAudience()).contains("fashion-api");
        // JIT provisioning (UserProvisioningServiceImpl) đọc email / email_verified từ chính các claim này
        assertThat(jwt.getClaimAsString("email")).isEqualTo("customer@fashion.local");
        assertThat(jwt.getClaimAsBoolean("email_verified")).isTrue();
    }

    @Test
    void validUserTokenReachesAProtectedEndpointWithItsRealmRole() throws Exception {
        mockMvc.perform(get("/api/v1/probe/authorities").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ROLE_USER")));
    }

    @Test
    void adminTokenCarriesThePermissionsOfItsGroup() throws Exception {
        mockMvc.perform(get("/api/v1/probe/authorities").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("group:manage")));
    }

    @Test
    void tokenIssuedForAnotherAudienceIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/probe/authorities").header("Authorization", "Bearer " + wrongAudienceToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithATamperedSignatureIsRejected() throws Exception {
        String[] parts = userToken.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "." + new StringBuilder(parts[2]).reverse();

        mockMvc.perform(get("/api/v1/probe/authorities").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void noTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/probe/authorities")).andExpect(status().isUnauthorized());
    }

    @Test
    void serviceAccountTokenReachesInternalEndpoints() throws Exception {
        mockMvc.perform(get("/internal/probe").header("Authorization", "Bearer " + serviceAccountToken))
                .andExpect(status().isOk());
    }

    @Test
    void userTokensAreForbiddenOnInternalEndpoints() throws Exception {
        mockMvc.perform(get("/internal/probe").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/internal/probe").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    private static String passwordToken(String clientId, String username, String password) throws Exception {
        return tokenOf(REALM, Map.of("grant_type", "password", "client_id", clientId, "username", username, "password", password));
    }

    private static String tokenOf(String realm, Map<String, String> form) throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(baseUrl + "/realms/" + realm + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encode(form))));
        assertThat(response.statusCode()).as("token endpoint: " + response.body()).isEqualTo(200);
        return JSON.readTree(response.body()).get("access_token").asText();
    }

    private static void enableDirectGrants(String masterToken, String clientId) throws Exception {
        JsonNode client = clientByClientId(masterToken, clientId);
        ((ObjectNode) client).put("directAccessGrantsEnabled", true);
        HttpResponse<String> response = send(adminRequest(masterToken, "/clients/" + client.get("id").asText())
                .PUT(HttpRequest.BodyPublishers.ofString(client.toString())));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(204);
    }

    private static void createPublicClientWithoutApiAudience(String masterToken, String clientId) throws Exception {
        String body = JSON.writeValueAsString(Map.of(
                "clientId", clientId, "publicClient", true, "directAccessGrantsEnabled", true, "standardFlowEnabled", false));
        HttpResponse<String> response = send(adminRequest(masterToken, "/clients").POST(HttpRequest.BodyPublishers.ofString(body)));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    }

    private static JsonNode clientByClientId(String masterToken, String clientId) throws Exception {
        HttpResponse<String> response = send(adminRequest(masterToken, "/clients?clientId=" + clientId).GET());
        assertThat(response.statusCode()).isEqualTo(200);
        return JSON.readTree(response.body()).get(0);
    }

    private static HttpRequest.Builder adminRequest(String masterToken, String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + "/admin/realms/" + REALM + path))
                .header("Authorization", "Bearer " + masterToken)
                .header("Content-Type", "application/json");
    }

    private static HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String encode(Map<String, String> form) {
        return form.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    @RestController
    static class ProbeController {

        @GetMapping("/api/v1/probe/authorities")
        List<String> authorities(Authentication authentication) {
            return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList();
        }

        @GetMapping("/internal/probe")
        String internal() {
            return "internal";
        }
    }
}
