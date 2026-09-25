package com.fashionstore.payment.config.security;

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
            default -> throw new BadJwtException("invalid");
        });
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

    private static Jwt jwt(String... realmRoles) {
        return Jwt.withTokenValue("token").header("alg", "none")
                .subject("kc-user-1")
                .claim("aud", List.of("fashion-api"))
                .claim("realm_access", Map.of("roles", List.of(realmRoles)))
                .build();
    }

    @RestController
    static class ProbeController {

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
