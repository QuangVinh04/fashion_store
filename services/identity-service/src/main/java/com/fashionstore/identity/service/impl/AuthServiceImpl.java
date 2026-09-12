package com.fashionstore.identity.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.redis.RedisService;
import com.fashionstore.identity.config.ErrorCode;
import com.fashionstore.common.util.VerificationCodeGenerator;
import com.fashionstore.identity.constant.PredefinedRole;
import com.fashionstore.identity.dto.AuthResponse;
import com.fashionstore.identity.dto.LoginRequest;
import com.fashionstore.identity.dto.RegisterRequest;
import com.fashionstore.identity.dto.VerifyEmailRequest;
import com.fashionstore.identity.entity.CustomUserDetails;
import com.fashionstore.identity.entity.Role;
import com.fashionstore.identity.entity.User;
import com.fashionstore.identity.entity.VerificationToken;
import com.fashionstore.identity.repository.RoleRepository;
import com.fashionstore.identity.repository.UserRepository;
import com.fashionstore.identity.repository.VerificationTokenRepository;
import com.fashionstore.identity.service.AuthService;
import com.fashionstore.identity.service.EmailService;
import com.fashionstore.identity.service.JwtService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
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

    UserRepository userRepository;
    RoleRepository roleRepository;
    PasswordEncoder passwordEncoder;
    AuthenticationManager authenticationManager;
    JwtService jwtService;
    EmailService emailService;
    RedisService redisService;


    @Override
    @Transactional
    public void register(RegisterRequest request) {
        // Kiểm tra email đã tồn tại chưa
        if (userRepository.existsByEmail(request.email())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        // Lấy role USER mặc định
        Role userRole = roleRepository.findByName(PredefinedRole.USER_ROLE)
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));
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
            throw new AppException(ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED);
        }
        // 2. Lấy OTP đang lưu trong Redis
        String cachedOtp = redisService.getValue(otpKey, String.class);
        if (cachedOtp == null) {
            throw new AppException(ErrorCode.OTP_INVALID_OR_EXPIRED);
        }
        // 3. So sánh OTP
        if (!cachedOtp.equals(inputOtp)) {
            Long currentAttempts = redisService.increment(attemptsKey);
            if (currentAttempts != null && currentAttempts == 1) {
                redisService.setExpire(attemptsKey, OTP_TTL_MINUTES, TimeUnit.MINUTES);
            }
            if (currentAttempts != null && currentAttempts >= MAX_ATTEMPTS) {
                redisService.deleteValue(otpKey);
                throw new AppException(ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED);
            }
            throw new AppException(ErrorCode.OTP_INVALID_OR_EXPIRED);
        }
        // 4. Xác minh thành công -> Kích hoạt tài khoản
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
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
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // 2. Kiểm tra trạng thái verified
        if (Boolean.TRUE.equals(user.getIsEmailVerified())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_VERIFIED);
        }

        // Kiểm tra Cooldown 60s
        String cooldownKey = COOLDOWN_KEY_PREFIX + email;
        if (redisService.existsValue(cooldownKey)) {
            throw new AppException(ErrorCode.RESEND_COOLDOWN_ACTIVE);
        }

        redisService.deleteValue(ATTEMPTS_KEY_PREFIX + email);
        sendAndStoreOtp(user.getEmail(), user.getFullName());
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // Spring Security tự: load user từ DB + so sánh password
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        // Đến đây = xác thực thành công
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();

        if(!Boolean.TRUE.equals(user.getIsEmailVerified())) {
            throw new AppException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        String token = jwtService.generateAccessToken(user);
        log.info("User logged in: {}", user.getEmail());
        return buildAuthResponse(user, token);
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
                .accessToken(token)
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roles(roles)
                .build();
    }
}
