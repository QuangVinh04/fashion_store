package com.fashionstore.order.shared.config.security;

import com.fashionstore.common.autoconfigure.CommonSecurityAutoConfiguration;
import com.fashionstore.common.security.Permission;
import com.fashionstore.common.security.PermissionNames;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import com.fashionstore.common.security.CurrentUserProvider;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
            case "staff-token" -> staffJwt();
            case "no-permission-token" -> jwt("ADMIN");
            case "service-token" -> serviceAccountJwt();
            default -> throw new BadJwtException("invalid");
        });
    }

    @Test
    void guestCartRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/cart")).andExpect(status().isUnauthorized());
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
    void storefrontBffOriginCanReachCart() throws Exception {
        mockMvc.perform(get("/api/v1/cart")
                        .header("Origin", "http://localhost:8083")
                        .header("Authorization", "Bearer user-token"))
                .andExpect(status().isOk());
    }

    @Test
    void userTokenIsForbiddenOnAdminMethod() throws Exception {
        mockMvc.perform(get("/api/v1/admin/promotions").header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void roleAdminWithoutPermissionIsForbiddenOnAdminArea() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders").header("Authorization", "Bearer no-permission-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffTokenReadsOrdersAndApprovesReturns() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders").header("Authorization", "Bearer staff-token"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/orders/returns/r-1/approve").header("Authorization", "Bearer staff-token"))
                .andExpect(status().isOk());
    }

    @Test
    void staffTokenLacksRefundPromotionManageAndRevenue() throws Exception {
        mockMvc.perform(post("/api/v1/admin/orders/returns/r-1/refund").header("Authorization", "Bearer staff-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/promotions").header("Authorization", "Bearer staff-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/dashboard").header("Authorization", "Bearer staff-token"))
                .andExpect(status().isForbidden());
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

    // Token nhân viên mặc định: client role = các quyền isStaffDefault
    private static Jwt staffJwt() {
        List<String> permissions = Arrays.stream(Permission.values())
                .filter(Permission::isStaffDefault)
                .map(Permission::authority)
                .toList();
        return Jwt.withTokenValue("token").header("alg", "none")
                .subject("kc-staff-1")
                .claim("aud", List.of("fashion-api"))
                .claim("resource_access", Map.of("fashion-api", Map.of("roles", permissions)))
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

        @PreAuthorize("hasAuthority('" + PermissionNames.PROMOTION_READ + "')")
        @GetMapping("/api/v1/admin/promotions")
        String promotions() {
            return "promotions";
        }

        @PreAuthorize("hasAuthority('" + PermissionNames.PROMOTION_MANAGE + "')")
        @PostMapping("/api/v1/admin/promotions")
        String createPromotion() {
            return "created";
        }

        @PreAuthorize("hasAuthority('" + PermissionNames.ORDER_READ + "')")
        @GetMapping("/api/v1/admin/orders")
        String adminOrders() {
            return "orders";
        }

        @PreAuthorize("hasAuthority('" + PermissionNames.RETURN_MANAGE + "')")
        @PostMapping("/api/v1/admin/orders/returns/{id}/approve")
        String approve() {
            return "approved";
        }

        @PreAuthorize("hasAuthority('" + PermissionNames.REFUND_APPROVE + "')")
        @PostMapping("/api/v1/admin/orders/returns/{id}/refund")
        String refund() {
            return "refunded";
        }

        @PreAuthorize("hasAuthority('" + PermissionNames.REVENUE_READ + "')")
        @GetMapping("/api/v1/admin/dashboard")
        String dashboard() {
            return "dashboard";
        }
    }
}
