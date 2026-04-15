package com.fashionstore.clothes_retail_api.modules.auth.service.impl;

import com.fashionstore.clothes_retail_api.common.exception.AppException;
import com.fashionstore.clothes_retail_api.common.exception.ErrorCode;
import com.fashionstore.clothes_retail_api.modules.auth.constant.PredefinedRole;
import com.fashionstore.clothes_retail_api.modules.auth.dto.AuthResponse;
import com.fashionstore.clothes_retail_api.modules.auth.dto.LoginResquest;
import com.fashionstore.clothes_retail_api.modules.auth.dto.RegisterRequest;
import com.fashionstore.clothes_retail_api.modules.auth.entity.Role;
import com.fashionstore.clothes_retail_api.modules.auth.entity.User;
import com.fashionstore.clothes_retail_api.modules.auth.repository.RoleRepository;
import com.fashionstore.clothes_retail_api.modules.auth.repository.UserRepository;
import com.fashionstore.clothes_retail_api.modules.auth.service.AuthService;
import com.fashionstore.clothes_retail_api.modules.auth.service.JwtService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthServiceImpl implements AuthService {

    UserRepository userRepository;
    RoleRepository roleRepository;
    PasswordEncoder passwordEncoder;
    AuthenticationManager authenticationManager;
    JwtService jwtService;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Kiểm tra email đã tồn tại chưa
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        // Lấy role USER mặc định
        Role userRole = roleRepository.findByName(PredefinedRole.USER_ROLE)
                .orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));
        // Tạo user mới
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .isActive(true)
                .isEmailVerified(false)
                .roles(new HashSet<>(Set.of(userRole)))
                .build();
        userRepository.save(user);
        log.info("New user registered: {}", user.getEmail());
        // Tạo token ngay sau khi đăng ký
        String token = jwtService.generateAccessToken(user);
        return buildAuthResponse(user, token);
    }

    @Override
    public AuthResponse login(LoginResquest request) {
        // Spring Security tự: load user từ DB + so sánh password
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        // Đến đây = xác thực thành công
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        }

        String token = jwtService.generateAccessToken(user);
        log.info("User logged in: {}", user.getEmail());
        return buildAuthResponse(user, token);
    }

    // Helper: build response
    private AuthResponse buildAuthResponse(User user, String token) {
        Set<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
        return AuthResponse.builder()
                .accessToken(token)
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roles(roles)
                .expiresIn(jwtService.getValidDuration())
                .build();
    }
}
