package com.fashionstore.identity.controller;

import com.nimbusds.jose.jwk.RSAKey;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Khóa công khai để các dịch vụ xác minh JWT")
@RequiredArgsConstructor
public class JwkController {

    private final RSAKey rsaKey;

    @GetMapping("/jwks")
    public ResponseEntity<Map<String, Object>> getJwkSet() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5)).cachePublic())
                .body(Map.of("keys", List.of(rsaKey.toPublicJWK().toJSONObject())));
    }
}
