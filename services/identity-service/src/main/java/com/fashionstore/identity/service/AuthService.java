package com.fashionstore.identity.service;

import com.fashionstore.identity.dto.auth.AuthResponse;
import com.fashionstore.identity.dto.auth.LoginRequest;
import com.fashionstore.identity.dto.auth.RegisterRequest;
import com.fashionstore.identity.dto.auth.VerifyEmailRequest;

public interface AuthService {
    void register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    void verifyEmail(VerifyEmailRequest token);
    void resendVerification(String email);


}
