package com.fashionstore.identity.service;

import com.fashionstore.identity.dto.AuthResponse;
import com.fashionstore.identity.dto.LoginRequest;
import com.fashionstore.identity.dto.RegisterRequest;
import com.fashionstore.identity.dto.VerifyEmailRequest;

public interface AuthService {
    void register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    void verifyEmail(VerifyEmailRequest token);
    void resendVerification(String email);


}
