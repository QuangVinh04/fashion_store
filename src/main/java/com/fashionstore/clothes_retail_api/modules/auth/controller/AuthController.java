package com.fashionstore.clothes_retail_api.modules.auth.controller;

import com.fashionstore.clothes_retail_api.common.dto.ApiResponse;
import com.fashionstore.clothes_retail_api.modules.auth.dto.AuthResponse;
import com.fashionstore.clothes_retail_api.modules.auth.dto.LoginRequest;
import com.fashionstore.clothes_retail_api.modules.auth.dto.RegisterRequest;
import com.fashionstore.clothes_retail_api.modules.auth.service.AuthService;
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
