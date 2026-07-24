package com.fashionstore.identity.service;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.identity.config.ErrorCode;
import com.fashionstore.identity.entity.User;
import com.fashionstore.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentUserProvider {
    private final UserRepository userRepository;

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String email = jwt.getClaimAsString("email");
            if (email != null && !email.isBlank()) {
                return userRepository.findByEmail(email)
                        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
            }
            return userRepository.findById(jwt.getSubject())
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        }

        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    public String getCurrentUserId() {
        return getCurrentUser().getId();
    }
}
