package com.fashionstore.clothes_retail_api.modules.auth.controller;

import com.fashionstore.clothes_retail_api.common.dto.ApiResponse;
import com.fashionstore.clothes_retail_api.modules.auth.dto.AuthResponse;
import com.fashionstore.clothes_retail_api.modules.auth.dto.LoginResquest;
import com.fashionstore.clothes_retail_api.modules.auth.dto.RegisterRequest;
import com.fashionstore.clothes_retail_api.modules.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthController {

    AuthService authService;

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.<AuthResponse>builder()
                        .message("Đăng ký thành công")
                        .data(authService.register(request))
                        .build();
    }
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login( @Valid @RequestBody LoginResquest request) {
        return ApiResponse.<AuthResponse>builder()
                        .message("Đăng nhập thành công")
                        .data(authService.login(request))
                        .build();
    }
}
