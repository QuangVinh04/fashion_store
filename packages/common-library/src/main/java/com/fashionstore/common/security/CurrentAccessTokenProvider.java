package com.fashionstore.common.security;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class CurrentAccessTokenProvider {

    public String getCurrentTokenValue() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            Jwt jwt = jwtAuthenticationToken.getToken();
            if (jwt.getTokenValue() != null && !jwt.getTokenValue().isBlank()) {
                return jwt.getTokenValue();
            }
        }
        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }
}
