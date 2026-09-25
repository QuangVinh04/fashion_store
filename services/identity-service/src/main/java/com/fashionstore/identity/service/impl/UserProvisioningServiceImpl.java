package com.fashionstore.identity.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.identity.entity.User;
import com.fashionstore.identity.exception.IdentityErrorCode;
import com.fashionstore.identity.repository.UserRepository;
import com.fashionstore.identity.service.UserProvisioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProvisioningServiceImpl implements UserProvisioningService {

    private final UserRepository userRepository;

    // Transaction riêng: được gọi cả từ các luồng readOnly (xem hồ sơ, địa chỉ)
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void provision(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        boolean emailVerified = Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"));
        userRepository.findById(jwt.getSubject()).ifPresentOrElse(
                user -> syncEmail(user, email, emailVerified),
                () -> create(jwt, email, emailVerified));
    }

    private void create(Jwt jwt, String email, boolean emailVerified) {
        // Token service account (client_credentials) không có email — không phải khách hàng, không tạo user
        if (email == null || email.isBlank()) {
            throw new AppException(IdentityErrorCode.USER_NOT_FOUND);
        }
        ensureEmailFree(email);
        String name = jwt.getClaimAsString("name");
        userRepository.insertProvisionedUser(jwt.getSubject(), email, name != null ? name : email, emailVerified);
        log.info("Provisioned identity user {} from Keycloak", jwt.getSubject());
    }

    // Email do Keycloak quản lý; họ tên/điện thoại sửa trong hồ sơ identity nên không ghi đè
    private void syncEmail(User user, String email, boolean emailVerified) {
        if (email == null || email.equalsIgnoreCase(user.getEmail())) {
            return;
        }
        ensureEmailFree(email);
        user.setEmail(email);
        user.setIsEmailVerified(emailVerified);
        userRepository.save(user);
    }

    // Email trùng với user cũ khác id: user đó phải được import sang Keycloak với cùng id trước
    private void ensureEmailFree(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new AppException(IdentityErrorCode.ACCOUNT_LINK_CONFLICT);
        }
    }
}
