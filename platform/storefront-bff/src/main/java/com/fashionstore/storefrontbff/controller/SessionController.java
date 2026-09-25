package com.fashionstore.storefrontbff.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.storefrontbff.dto.SessionResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionController {

    // SPA gọi để biết trạng thái đăng nhập; token không bao giờ trả ra browser
    @GetMapping("/bff/session")
    public ApiResponse<SessionResponse> session(OAuth2AuthenticationToken authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser user)) {
            return ApiResponse.<SessionResponse>builder().data(SessionResponse.anonymous()).build();
        }
        // Role lấy từ Authentication (đã qua GrantedAuthoritiesMapper), không phải từ OidcUser principal
        SessionResponse session = new SessionResponse(
                true,
                user.getSubject(),
                user.getEmail(),
                user.getFullName(),
                authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList());
        return ApiResponse.<SessionResponse>builder().data(session).build();
    }
}
