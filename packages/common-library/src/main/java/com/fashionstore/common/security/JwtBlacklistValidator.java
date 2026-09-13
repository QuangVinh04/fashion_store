package com.fashionstore.common.security;

import com.fashionstore.common.redis.RedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

@RequiredArgsConstructor
public class JwtBlacklistValidator implements OAuth2TokenValidator<Jwt> {

    private static final String BLACKLIST_KEY_PREFIX = "auth:blacklist:";

    private final RedisService redisService;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String jti = jwt.getId();
        if (jti == null || jti.isBlank()) {
            return OAuth2TokenValidatorResult.success();
        }
        if (redisService.existsValue(BLACKLIST_KEY_PREFIX + jti)) {
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("token_revoked", "Access token has been revoked", null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}