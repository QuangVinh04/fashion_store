package com.fashionstore.identity.service;

import com.fashionstore.identity.dto.auth.*;
import org.springframework.security.oauth2.jwt.Jwt;

public interface AuthService {
    void register(RegisterRequest request);
    AuthResult login(LoginRequest request);
    AuthResult login(LoginRequest request, String clientIp);
    void verifyEmail(VerifyEmailRequest token);
    void resendVerification(String email);


    AuthResult refresh(String refreshToken);
    void logout(String refreshToken, Jwt jwt);
}
