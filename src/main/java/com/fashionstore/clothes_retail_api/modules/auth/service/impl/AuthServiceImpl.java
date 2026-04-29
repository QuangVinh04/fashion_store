package com.fashionstore.clothes_retail_api.modules.auth.service.impl;

import com.fashionstore.clothes_retail_api.common.exception.AppException;
import com.fashionstore.clothes_retail_api.common.exception.ErrorCode;
import com.fashionstore.clothes_retail_api.common.utils.OtpUtils;
import com.fashionstore.clothes_retail_api.modules.auth.constant.PredefinedRole;
import com.fashionstore.clothes_retail_api.modules.auth.dto.AuthResponse;
import com.fashionstore.clothes_retail_api.modules.auth.dto.LoginRequest;
import com.fashionstore.clothes_retail_api.modules.auth.dto.RegisterRequest;
import com.fashionstore.clothes_retail_api.modules.auth.entity.Role;
import com.fashionstore.clothes_retail_api.modules.auth.entity.User;
import com.fashionstore.clothes_retail_api.modules.auth.entity.VerificationToken;
import com.fashionstore.clothes_retail_api.modules.auth.repository.RoleRepository;
import com.fashionstore.clothes_retail_api.modules.auth.repository.UserRepository;
import com.fashionstore.clothes_retail_api.modules.auth.repository.VerificationTokenRepository;
import com.fashionstore.clothes_retail_api.modules.auth.service.AuthService;
import com.fashionstore.clothes_retail_api.modules.auth.service.EmailService;
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

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthServiceImpl implements AuthService {

    UserRepository userRepository;
    RoleRepository roleRepository;
    VerificationTokenRepository verificationTokenRepository;
    PasswordEncoder passwordEncoder;
    AuthenticationManager authenticationManager;
    JwtService jwtService;
    EmailService emailService;

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        // Kiểm tra email đã tồn tại chưa
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        // Lấy role USER mặc định
        Role userRole = roleRepository.findByName(PredefinedRole.USER_ROLE)
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));
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

        String verifyToken = OtpUtils.generateVerifyCode();
        VerificationToken vT = VerificationToken.builder()
                .token(verifyToken)
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        verificationTokenRepository.save(vT);
        emailService.sendVerificationEmail(user.getEmail(), user.getFullName(), verifyToken);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        // Spring Security tự: load user từ DB + so sánh password
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        // Đến đây = xác thực thành công
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if(Boolean.TRUE.equals(user.getIsEmailVerified())) {
            throw new AppException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        String token = jwtService.generateAccessToken(user);
        log.info("User logged in: {}", user.getEmail());
        return buildAuthResponse(user, token);
    }

    @Override
    @Transactional
    public void verifyEmail(String token) {
        VerificationToken vt = verificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new AppException(ErrorCode.VERIFICATION_TOKEN_INVALID));

        if (Boolean.TRUE.equals(vt.getUsed())) {
            throw new AppException(ErrorCode.VERIFICATION_TOKEN_INVALID);
        }
        if (vt.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AppException(ErrorCode.VERIFICATION_TOKEN_EXPIRED);
        }
        User user = vt.getUser();
        user.setIsEmailVerified(true);
        userRepository.save(user);

        vt.setUsed(true);
        verificationTokenRepository.save(vt);
    }
    @Override
    @Transactional
    public void resendVerification(String email) {
        // 1. Tìm user
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // 2. Kiểm tra trạng thái verified
        if (Boolean.TRUE.equals(user.getIsEmailVerified())) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }

        // Nếu tìm thấy thì dùng lại object đó, nếu không thì tạo mới gắn với user này
        VerificationToken vt = verificationTokenRepository.findByUserId(user.getId())
                .orElseGet(() -> VerificationToken.builder()
                        .user(user)
                        .build());

        String verifyToken = OtpUtils.generateVerifyCode();
        vt.setToken(verifyToken);
        vt.setExpiresAt(LocalDateTime.now().plusHours(24));
        vt.setUsed(false); // Đảm bảo token mới có thể sử dụng được

        verificationTokenRepository.save(vt);

        emailService.sendVerificationEmail(user.getEmail(), user.getFullName(), verifyToken);
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
                .build();
    }
}
