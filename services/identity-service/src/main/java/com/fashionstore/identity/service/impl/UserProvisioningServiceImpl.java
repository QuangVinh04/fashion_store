package com.fashionstore.identity.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.identity.entity.User;
import com.fashionstore.identity.exception.IdentityErrorCode;
import com.fashionstore.identity.repository.UserRepository;
import com.fashionstore.identity.service.UserProvisioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProvisioningServiceImpl implements UserProvisioningService {

    private final UserRepository userRepository;

    // Transaction riêng: được gọi cả từ các luồng readOnly (xem hồ sơ, địa chỉ).
    // Cả hai method công khai đều cần annotation: gọi this.provision(...) không đi qua proxy.
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void provision(Jwt jwt) {
        String email = jwt.getClaimAsString(StandardClaimNames.EMAIL);
        boolean emailVerified = Boolean.TRUE.equals(jwt.getClaimAsBoolean(StandardClaimNames.EMAIL_VERIFIED));
        provision(jwt.getSubject(), email, jwt.getClaimAsString(StandardClaimNames.NAME), emailVerified);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void provision(String subject, String email, String name, boolean emailVerified) {
        userRepository.findById(subject).ifPresentOrElse(
                user -> syncEmail(user, email, emailVerified),
                () -> create(subject, email, name, emailVerified));
    }

    private void create(String subject, String email, String name, boolean emailVerified) {
        // Token service account (client_credentials) không có email — không phải khách hàng, không tạo user
        if (email == null || email.isBlank()) {
            throw new AppException(IdentityErrorCode.USER_NOT_FOUND);
        }
        ensureEmailFree(email);
        userRepository.insertProvisionedUser(subject, email, name != null && !name.isBlank() ? name : email, emailVerified);
        log.info("Provisioned identity user {} from Keycloak", subject);
    }

    // Email do Keycloak quản lý; họ tên/điện thoại sửa trong hồ sơ identity nên không ghi đè
    private void syncEmail(User user, String email, boolean emailVerified) {
        if (email == null) {
            return;
        }
        if (email.equalsIgnoreCase(user.getEmail())) {
            markVerified(user, emailVerified);
            return;
        }
        ensureEmailFree(email);
        user.setEmail(email);
        user.setIsEmailVerified(emailVerified);
        userRepository.save(user);
    }

    // Chỉ nâng false -> true: REGISTER / sửa user phát lại luôn mang false, không được hạ cờ đã xác minh
    private void markVerified(User user, boolean emailVerified) {
        if (!emailVerified || Boolean.TRUE.equals(user.getIsEmailVerified())) {
            return;
        }
        user.setIsEmailVerified(true);
        userRepository.save(user);
    }

    // Email trùng với user cũ khác id: user đó phải được import sang Keycloak với cùng id trước
    private void ensureEmailFree(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new AppException(IdentityErrorCode.ACCOUNT_LINK_CONFLICT);
        }
    }
}
