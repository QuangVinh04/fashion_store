package com.fashionstore.payment.shared.config.security;

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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
            // Service account thật: client_credentials mang client role `internal-caller` của fashion-api
            case "service-token" -> serviceAccountJwt();
            // Realm role trùng tên KHÔNG phải service account
            case "realm-role-token" -> jwt("INTERNAL_CALLER");
            default -> throw new BadJwtException("invalid");
        });
    }

    @Test
    void customerCannotCallInternalWalletRefund() throws Exception {
        mockMvc.perform(get("/internal/wallets/u-1/refund").header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void serviceAccountCanCallInternalWalletRefund() throws Exception {
        mockMvc.perform(get("/internal/wallets/u-1/refund").header("Authorization", "Bearer service-token"))
                .andExpect(status().isOk());
    }

    @Test
    void aRealmRoleWithTheSameNameDoesNotGrantInternalAccess() throws Exception {
        mockMvc.perform(get("/internal/wallets/u-1/refund").header("Authorization", "Bearer realm-role-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void vnpayCallbackNeedsNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/payments/vnpay/ipn")).andExpect(status().isOk());
    }

    @Test
    void paymentWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/payments/p-1")).andExpect(status().isUnauthorized());
    }

    @Test
    void gatewayUserHeadersAreNoLongerTrusted() throws Exception {
        mockMvc.perform(get("/api/v1/payments/p-1")
                        .header("X-User-Id", "attacker")
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/payments/p-1").header("Authorization", "Bearer forged"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userTokenReachesPayment() throws Exception {
        mockMvc.perform(get("/api/v1/payments/p-1").header("Authorization", "Bearer user-token"))
                .andExpect(status().isOk());
    }

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

        @GetMapping("/internal/wallets/{id}/refund")
        String internalRefund(@PathVariable String id) {
            return id;
        }

        @GetMapping("/api/v1/payments/vnpay/ipn")
        String ipn() {
            return "ipn";
        }

        @GetMapping("/api/v1/payments/{id}")
        String payment(@PathVariable String id) {
            return id;
        }
    }
}
