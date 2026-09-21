package com.fashionstore.identity.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.redis.RedisService;
import com.fashionstore.identity.exception.IdentityErrorCode;
import com.fashionstore.common.util.VerificationCodeGenerator;
import com.fashionstore.identity.constant.PredefinedRole;
import com.fashionstore.identity.dto.auth.*;
import com.fashionstore.identity.entity.CustomUserDetails;
import com.fashionstore.identity.entity.Role;
import com.fashionstore.identity.entity.User;
import com.fashionstore.identity.repository.RoleRepository;
import com.fashionstore.identity.repository.UserRepository;
import com.fashionstore.identity.service.AuthService;
import com.fashionstore.identity.service.EmailService;
import com.fashionstore.identity.service.JwtService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthServiceImpl implements AuthService {

    static String OTP_KEY_PREFIX = "auth:verify:otp:";
    static String ATTEMPTS_KEY_PREFIX = "auth:verify:attempts:";
    static String COOLDOWN_KEY_PREFIX = "auth:verify:resend-cooldown:";

    static long OTP_TTL_MINUTES = 10;
    static long COOLDOWN_TTL_SECONDS = 60;
    static int MAX_ATTEMPTS = 5;

    static String REFRESH_KEY_PREFIX = "auth:refresh:";
    static String USER_REFRESH_KEY_PREFIX = "auth:user-refresh:";
    static String BLACKLIST_KEY_PREFIX = "auth:blacklist:";
    static String LOGIN_ATTEMPTS_KEY_PREFIX = "auth:login:attempts:";
    static long LOGIN_WINDOW_MINUTES = 5;
    static long MAX_LOGIN_ATTEMPTS = 5;

    @NonFinal
    @Value("${security.jwt.refresh-token-ttl-days:7}") // 7 ngày
    private long refreshTokenTtlDays;

    UserRepository userRepository;
    RoleRepository roleRepository;
    PasswordEncoder passwordEncoder;
    AuthenticationManager authenticationManager;
    JwtService jwtService;
    EmailService emailService;
    RedisService redisService;
    JwtDecoder jwtDecoder;


    @Override
    @Transactional
    public void register(RegisterRequest request) {
        // Kiểm tra email đã tồn tại chưa
        if (userRepository.existsByEmail(request.email())) {
            throw new AppException(IdentityErrorCode.EMAIL_ALREADY_EXISTS);
        }
        // Lấy role USER mặc định
        Role userRole = roleRepository.findByName(PredefinedRole.USER_ROLE)
                .orElseThrow(() -> new AppException(IdentityErrorCode.ROLE_NOT_FOUND));
        // Tạo user mới
        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .phone(request.phone())
                .isActive(true)
                .isEmailVerified(false)
                .roles(new HashSet<>(Set.of(userRole)))
                .build();
        userRepository.save(user);
        log.info("New user registered: {}", user.getEmail());

        // Sinh OTP và lưu vào Redis
        sendAndStoreOtp(user.getEmail(), user.getFullName());
    }

    @Override
    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        String email = request.email();
        String inputOtp = request.otp();
        String otpKey = OTP_KEY_PREFIX + email;
        String attemptsKey = ATTEMPTS_KEY_PREFIX + email;

        // 1. Kiểm tra số lần nhập sai
        Object attemptsVal = redisService.getValue(attemptsKey);
        int attempts = attemptsVal != null ? Integer.parseInt(attemptsVal.toString()) : 0;
        if (attempts >= MAX_ATTEMPTS) {
            // Xóa luôn OTP để ép người dùng phải gửi lại mã mới
            redisService.deleteValue(otpKey);
            throw new AppException(IdentityErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED);
        }
        // 2. Lấy OTP đang lưu trong Redis
        String cachedOtp = redisService.getValue(otpKey, String.class);
        if (cachedOtp == null) {
            throw new AppException(IdentityErrorCode.OTP_INVALID_OR_EXPIRED);
        }
        // 3. So sánh OTP
        if (!cachedOtp.equals(inputOtp)) {
            Long currentAttempts = redisService.increment(attemptsKey);
            if (currentAttempts != null && currentAttempts == 1) {
                redisService.setExpire(attemptsKey, OTP_TTL_MINUTES, TimeUnit.MINUTES);
            }
            if (currentAttempts != null && currentAttempts >= MAX_ATTEMPTS) {
                redisService.deleteValue(otpKey);
                throw new AppException(IdentityErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED);
            }
            throw new AppException(IdentityErrorCode.OTP_INVALID_OR_EXPIRED);
        }
        // 4. Xác minh thành công -> Kích hoạt tài khoản
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(IdentityErrorCode.USER_NOT_FOUND));
        user.setIsEmailVerified(true);
        userRepository.save(user);

        // 5. Dọn dẹp key trên Redis
        redisService.deleteKeys(List.of(otpKey, attemptsKey));
        log.info("Email verified successfully for: {}", email);
    }

    @Override
    @Transactional
    public void resendVerification(String email) {
        // 1. Tìm user
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(IdentityErrorCode.USER_NOT_FOUND));

        // 2. Kiểm tra trạng thái verified
        if (Boolean.TRUE.equals(user.getIsEmailVerified())) {
            throw new AppException(IdentityErrorCode.EMAIL_ALREADY_VERIFIED);
        }

        // Kiểm tra Cooldown 60s
        String cooldownKey = COOLDOWN_KEY_PREFIX + email;
        if (redisService.existsValue(cooldownKey)) {
            throw new AppException(IdentityErrorCode.RESEND_COOLDOWN_ACTIVE);
        }

        redisService.deleteValue(ATTEMPTS_KEY_PREFIX + email);
        sendAndStoreOtp(user.getEmail(), user.getFullName());
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResult login(LoginRequest request) {
        return login(request, "unknown");
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResult login(LoginRequest request, String clientIp) {
        String loginKey = LOGIN_ATTEMPTS_KEY_PREFIX
                + sha256Hex(clientIp + "|" + request.getEmail().toLowerCase());
        Object attemptsValue = redisService.getValue(loginKey);
        if (attemptsValue != null && Long.parseLong(attemptsValue.toString()) >= MAX_LOGIN_ATTEMPTS) {
            throw new AppException(IdentityErrorCode.LOGIN_RATE_LIMITED);
        }

        // Spring Security tự: load user từ DB + so sánh password
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        } catch (AuthenticationException exception) {
            Long attempts = redisService.increment(loginKey);
            if (attempts != null && attempts == 1) {
                redisService.setExpire(loginKey, LOGIN_WINDOW_MINUTES, TimeUnit.MINUTES);
            }
            log.warn("Login failed for email {} from {}", request.getEmail(), clientIp);
            if (attempts != null && attempts >= MAX_LOGIN_ATTEMPTS) {
                throw new AppException(IdentityErrorCode.LOGIN_RATE_LIMITED);
            }
            throw new AppException(IdentityErrorCode.INVALID_CREDENTIALS);
        }
        redisService.deleteValue(loginKey);

        // Đến đây = xác thực thành công
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();

        if(!Boolean.TRUE.equals(user.getIsEmailVerified())) {
            throw new AppException(IdentityErrorCode.EMAIL_NOT_VERIFIED);
        }

        // 1. Sinh Access Token và Refresh Token qua JwtService
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);


        // 2. Lưu Refresh Token vào Redis (Key: auth:refresh:{sha256} -> Value: userId)
        storeRefreshToken(refreshToken, user.getId());

        log.info("User logged in successfully: {}", user.getEmail());
        AuthResponse response = buildAuthResponse(user, accessToken);
        return new AuthResult(response, refreshToken);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResult refresh(String oldRefreshToken) {
        if (oldRefreshToken == null || oldRefreshToken.isBlank()) {
            throw new AppException(IdentityErrorCode.REFRESH_TOKEN_INVALID);
        }
        // 1. Xác minh chữ ký + claims trước khi tin tưởng token (JwtDecoder dùng public key local)
        Jwt decoded;
        try {
            decoded = jwtDecoder.decode(oldRefreshToken);
        } catch (JwtException e) {
            throw new AppException(IdentityErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (!"REFRESH_TOKEN".equals(decoded.getClaimAsString("token_type")) || decoded.getSubject() == null) {
            throw new AppException(IdentityErrorCode.REFRESH_TOKEN_INVALID);
        }
        String subject = decoded.getSubject();
        String refreshHash = sha256Hex(oldRefreshToken);
        String refreshKey = REFRESH_KEY_PREFIX + refreshHash;

        // 2. Kiểm tra Refresh Token trong Redis
        String userId = redisService.getValue(refreshKey, String.class);
        if (userId == null) {
            // 2b. Reuse detection: token còn chữ ký hợp lệ nhưng không còn trong Redis
            //     => token đã bị rotate trước đó, khả năng bị đánh cắp -> thu hồi cả session
            String currentHash = redisService.getValue(USER_REFRESH_KEY_PREFIX + subject, String.class);
            if (currentHash != null && !currentHash.equals(refreshHash)) {
                redisService.deleteValue(REFRESH_KEY_PREFIX + currentHash);
                redisService.deleteValue(USER_REFRESH_KEY_PREFIX + subject);
                log.warn("Refresh token reuse detected, session revoked for user {}", subject);
            }
            throw new AppException(IdentityErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (!subject.equals(userId)) {
            throw new AppException(IdentityErrorCode.REFRESH_TOKEN_INVALID);
        }

        // 3. Tìm User
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(IdentityErrorCode.USER_NOT_FOUND));
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new AppException(IdentityErrorCode.ACCOUNT_DISABLED);
        }

        // 4. REFRESH TOKEN ROTATION: Xóa token cũ ngay lập tức
        redisService.deleteValue(refreshKey);

        // 5. Sinh cặp Token MỚI qua JwtService
        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user);

        // 6. Lưu token mới vào Redis
        storeRefreshToken(newRefreshToken, user.getId());
        log.info("Token rotated successfully for: {}", user.getEmail());
        AuthResponse response = buildAuthResponse(user, newAccessToken);
        return new AuthResult(response, newRefreshToken);
    }

    @Override
    public void logout(String refreshToken, Jwt jwt) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            String refreshHash = sha256Hex(refreshToken);
            redisService.deleteValue(REFRESH_KEY_PREFIX + refreshHash);
            // Dọn index per-user nếu đây là refresh token hiện tại của user
            if (jwt != null && jwt.getSubject() != null) {
                String currentHash = redisService.getValue(
                        USER_REFRESH_KEY_PREFIX + jwt.getSubject(), String.class);
                if (refreshHash.equals(currentHash)) {
                    redisService.deleteValue(USER_REFRESH_KEY_PREFIX + jwt.getSubject());
                }
            }
        }
        if (jwt != null && jwt.getId() != null && jwt.getExpiresAt() != null) {
            redisService.setUntil(
                    BLACKLIST_KEY_PREFIX + jwt.getId(),
                    "1",
                    jwt.getExpiresAt().toEpochMilli());
        }
        log.info("User logged out successfully");
    }

    // Lưu refresh token dạng SHA-256 hash làm key + index per-user (reuse detection)
    private void storeRefreshToken(String refreshToken, String userId) {
        String refreshHash = sha256Hex(refreshToken);
        redisService.setWithTTL(
                REFRESH_KEY_PREFIX + refreshHash,
                userId,
                refreshTokenTtlDays,
                TimeUnit.DAYS
        );
        redisService.setWithTTL(
                USER_REFRESH_KEY_PREFIX + userId,
                refreshHash,
                refreshTokenTtlDays,
                TimeUnit.DAYS
        );
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private void sendAndStoreOtp(String email, String fullName) {
        String otp = VerificationCodeGenerator.generateSixDigitCode();
        // 1. Lưu OTP vào Redis với TTL 10 phút
        redisService.setWithTTL(OTP_KEY_PREFIX + email, otp, OTP_TTL_MINUTES, TimeUnit.MINUTES);
        // 2. Đặt cờ Cooldown 60 giây
        redisService.setWithTTL(COOLDOWN_KEY_PREFIX + email, "1", COOLDOWN_TTL_SECONDS, TimeUnit.SECONDS);
        // 3. Bắn event gửi email qua Transactional Outbox
        emailService.sendVerificationEmail(email, fullName, otp);
        log.info("Verification OTP sent to email: {}", email);
    }


    // Helper: build response
    private AuthResponse buildAuthResponse(User user, String token) {
        Set<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
        return AuthResponse.builder()
                .userId(user.getId())
                .accessToken(token)
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roles(roles)
                .build();
    }
}
