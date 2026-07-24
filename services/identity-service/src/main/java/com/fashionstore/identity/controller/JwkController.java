package com.fashionstore.identity.controller;

import com.nimbusds.jose.jwk.RSAKey;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class JwkController {

    private final RSAKey rsaKey;

    @GetMapping("/jwks")
    public Map<String, Object> getJwkSet() {
        return Map.of("keys", List.of(rsaKey.toPublicJWK().toJSONObject()));
    }
}
