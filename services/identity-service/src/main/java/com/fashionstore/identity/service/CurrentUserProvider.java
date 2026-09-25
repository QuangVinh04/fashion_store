package com.fashionstore.identity.service;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.identity.entity.User;
import com.fashionstore.identity.exception.IdentityErrorCode;
import com.fashionstore.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentUserProvider {
    private final UserRepository userRepository;
    private final UserProvisioningService userProvisioningService;

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AppException(IdentityErrorCode.UNAUTHENTICATED);
        }
        try {
            userProvisioningService.provision(jwt);
        } catch (DataIntegrityViolationException concurrentFirstRequest) {
            // Request song song đã tạo user này trước — đọc lại bên dưới
        }
        // Nạp trong transaction của caller để caller sửa/lưu được entity
        return userRepository.findById(jwt.getSubject())
                .orElseThrow(() -> new AppException(IdentityErrorCode.USER_NOT_FOUND));
    }

    public String getCurrentUserId() {
        return getCurrentUser().getId();
    }
}
