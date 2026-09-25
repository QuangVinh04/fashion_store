package com.fashionstore.order.config.security;

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
import org.springframework.web.bind.annotation.RestController;
import com.fashionstore.common.security.CurrentUserProvider;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
    void guestCartNeedsNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/cart")).andExpect(status().isOk());
    }

    @Test
    void ordersWithoutTokenAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void gatewayUserHeadersAreNoLongerTrusted() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .header("X-User-Id", "attacker")
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/orders").header("Authorization", "Bearer forged"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void currentUserIsTheKeycloakSubject() throws Exception {
        mockMvc.perform(get("/api/v1/orders").header("Authorization", "Bearer user-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("kc-user-1"));
    }

    @Test
    void userTokenIsForbiddenOnAdminMethod() throws Exception {
        mockMvc.perform(get("/api/v1/admin/promotions").header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminTokenPassesAdminMethod() throws Exception {
        mockMvc.perform(get("/api/v1/admin/promotions").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void internalEndpointWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/internal/orders/o-1")).andExpect(status().isUnauthorized());
    }

    @Test
    void legacyInternalSecretIsNoLongerAccepted() throws Exception {
        mockMvc.perform(get("/internal/orders/o-1").header("X-Internal-Token", "fashion-store-internal-secret-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userTokenIsForbiddenOnInternalEndpoint() throws Exception {
        mockMvc.perform(get("/internal/orders/o-1").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void serviceAccountTokenReachesInternalEndpoint() throws Exception {
        mockMvc.perform(get("/internal/orders/o-1").header("Authorization", "Bearer service-token"))
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

        @GetMapping("/internal/orders/o-1")
        String internal() {
            return "internal";
        }

        private final CurrentUserProvider currentUserProvider;

        ProbeController(CurrentUserProvider currentUserProvider) {
            this.currentUserProvider = currentUserProvider;
        }

        @GetMapping("/api/v1/cart")
        String cart() {
            return "cart";
        }

        @GetMapping("/api/v1/orders")
        String orders() {
            return currentUserProvider.getCurrentUserId();
        }

        @PreAuthorize("hasRole('ADMIN')")
        @GetMapping("/api/v1/admin/promotions")
        String promotions() {
            return "promotions";
        }
    }
}
