package com.fashionstore.identity.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.identity.dto.AuthResponse;
import com.fashionstore.identity.dto.LoginRequest;
import com.fashionstore.identity.dto.RegisterRequest;
import com.fashionstore.identity.service.AuthService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthController {

    AuthService authService;

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ApiResponse.<Void>builder()
                        .message("Đăng ký thành công")
                        .build();
    }
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login( @Valid @RequestBody LoginRequest request) {
        return ApiResponse.<AuthResponse>builder()
                        .message("Đăng nhập thành công")
                        .data(authService.login(request))
                        .build();
    }

    @GetMapping("/verify-email")
    public ApiResponse<Void> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ApiResponse.<Void>builder()
                .message("Xác nhận email thành công, bạn có thể đăng nhập")
                .build();
    }
    @PostMapping("/resend-verification")
    public ApiResponse<Void> resendVerification(@RequestParam String email) {
        authService.resendVerification(email);
        return ApiResponse.<Void>builder()
                .message("Email xác nhận đã được gửi lại")
                .build();
    }
}
