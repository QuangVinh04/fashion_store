package com.fashionstore.catalog.config.security;

import com.fashionstore.common.autoconfigure.CommonSecurityAutoConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Chuỗi security thật + converter Keycloak thật; chỉ JwtDecoder được giả lập
@WebMvcTest
@ContextConfiguration(classes = {SecurityConfig.class, SecurityConfigTest.ProbeController.class})
@ImportAutoConfiguration(CommonSecurityAutoConfiguration.class)
class SecurityConfigTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @BeforeEach
    void tokens() {
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> switch (invocation.<String>getArgument(0)) {
            case "user-token" -> jwt("USER");
            case "admin-token" -> jwt("ADMIN", "USER");
            case "service-token" -> serviceAccountJwt();
            default -> throw new BadJwtException("invalid");
        });
    }

    @Test
    void publicCatalogReadNeedsNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/products/p-1")).andExpect(status().isOk());
    }

    @Test
    void adminEndpointWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/variants/v-1")).andExpect(status().isUnauthorized());
    }

    @Test
    void gatewayUserHeadersAreNoLongerTrusted() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/variants/v-1")
                        .header("X-User-Id", "attacker")
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/variants/v-1").header("Authorization", "Bearer forged"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userTokenIsForbiddenOnAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/variants/v-1").header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminTokenReachesAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/variants/v-1").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void userTokenReachesAuthenticatedEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/check").header("Authorization", "Bearer user-token"))
                .andExpect(status().isOk());
    }

    @Test
    void internalEndpointWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/internal/inventory/low-stock/count")).andExpect(status().isUnauthorized());
    }

    @Test
    void legacyInternalSecretIsNoLongerAccepted() throws Exception {
        mockMvc.perform(get("/internal/inventory/low-stock/count").header("X-Internal-Token", "fashion-store-internal-secret-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userTokenIsForbiddenOnInternalEndpoint() throws Exception {
        mockMvc.perform(get("/internal/inventory/low-stock/count").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void serviceAccountTokenReachesInternalEndpoint() throws Exception {
        mockMvc.perform(get("/internal/inventory/low-stock/count").header("Authorization", "Bearer service-token"))
                .andExpect(status().isOk());
    }

    // Token client_credentials của service account: không có realm role, chỉ client role internal-caller
    private static Jwt serviceAccountJwt() {
        return Jwt.withTokenValue("token").header("alg", "none")
                .subject("service-account-order-service")
                .claim("aud", List.of("fashion-api"))
                .claim("resource_access", Map.of("fashion-api", Map.of("roles", List.of("internal-caller"))))
                .build();
    }

    private static Jwt jwt(String... realmRoles) {
        return Jwt.withTokenValue("token").header("alg", "none")
                .subject("kc-user-1")
                .claim("aud", List.of("fashion-api"))
                .claim("realm_access", Map.of("roles", List.of(realmRoles)))
                .build();
    }

    @RestController
    static class ProbeController {

        @GetMapping("/internal/inventory/low-stock/count")
        String internal() {
            return "internal";
        }

        @GetMapping("/api/v1/products/{id}")
        String product(@PathVariable String id) {
            return id;
        }

        @GetMapping("/api/v1/inventory/variants/{id}")
        String inventory(@PathVariable String id) {
            return id;
        }

        @PostMapping("/api/v1/inventory/check")
        String check() {
            return "ok";
        }
    }
}
