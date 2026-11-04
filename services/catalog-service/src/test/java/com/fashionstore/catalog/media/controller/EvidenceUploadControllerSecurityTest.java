package com.fashionstore.catalog.media.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fashionstore.catalog.media.dto.PresignUploadResponse;
import com.fashionstore.catalog.media.service.MediaFileService;
import com.fashionstore.catalog.shared.config.security.SecurityConfig;
import com.fashionstore.common.autoconfigure.CommonSecurityAutoConfiguration;
import com.fashionstore.common.security.PermissionNames;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Controller thật + chuỗi security thật: kiểm tra @PreAuthorize của POST /payments/presign
@WebMvcTest(EvidenceUploadController.class)
@ContextConfiguration(classes = {SecurityConfig.class, EvidenceUploadController.class})
@ImportAutoConfiguration(CommonSecurityAutoConfiguration.class)
class EvidenceUploadControllerSecurityTest {

    private static final String PAYMENTS_PRESIGN = "/api/v1/files/evidence/payments/presign";
    private static final String BODY = "{\"filename\":\"a.png\",\"contentType\":\"image/png\",\"sizeBytes\":10}";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    MediaFileService mediaFileService;

    @BeforeEach
    void tokens() {
        when(mediaFileService.presignUpload(any(), any())).thenReturn(PresignUploadResponse.builder().build());
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> switch (invocation.<String>getArgument(0)) {
            case "order-manage" -> permissionJwt(PermissionNames.ORDER_MANAGE);
            case "refund-approve" -> permissionJwt(PermissionNames.REFUND_APPROVE);
            case "review-read" -> permissionJwt(PermissionNames.REVIEW_READ);
            case "role-admin-only" -> roleAdminJwt();
            default -> throw new BadJwtException("invalid");
        });
    }

    @Test
    void orderManageCanPresignPaymentEvidence() throws Exception {
        perform("order-manage").andExpect(status().isOk());
    }

    @Test
    void refundApproveCanPresignPaymentEvidence() throws Exception {
        perform("refund-approve").andExpect(status().isOk());
    }

    @Test
    void unrelatedPermissionIsForbidden() throws Exception {
        perform("review-read").andExpect(status().isForbidden());
    }

    @Test
    void roleAdminOnlyIsForbidden() throws Exception {
        perform("role-admin-only").andExpect(status().isForbidden());
    }

    private ResultActions perform(String token) throws Exception {
        return mockMvc.perform(post(PAYMENTS_PRESIGN)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY));
    }

    private static Jwt permissionJwt(String permission) {
        return Jwt.withTokenValue("token").header("alg", "none")
                .subject("kc-staff-1")
                .claim("aud", List.of("fashion-api"))
                .claim("resource_access", Map.of("fashion-api", Map.of("roles", List.of(permission))))
                .build();
    }

    private static Jwt roleAdminJwt() {
        return Jwt.withTokenValue("token").header("alg", "none")
                .subject("kc-admin-1")
                .claim("aud", List.of("fashion-api"))
                .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                .build();
    }
}
