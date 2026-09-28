package com.fashionstore.identity.service.impl;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.identity.client.KeycloakAdminClient;
import com.fashionstore.identity.dto.user.AdminUpdateUserRequest;
import com.fashionstore.identity.dto.user.AdminUserDetailResponse;
import com.fashionstore.identity.dto.user.AdminUserResponse;
import com.fashionstore.identity.dto.user.UserAddressResponse;
import com.fashionstore.identity.entity.User;
import com.fashionstore.identity.exception.IdentityErrorCode;
import com.fashionstore.identity.mapper.UserAddressMapper;
import com.fashionstore.identity.mapper.UserMapper;
import com.fashionstore.identity.repository.UserAddressRepository;
import com.fashionstore.identity.repository.UserRepository;
import com.fashionstore.identity.service.AdminUserService;
import com.fashionstore.identity.service.CurrentUserProvider;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminUserServiceImpl implements AdminUserService {

    UserRepository userRepository;
    UserAddressRepository userAddressRepository;
    KeycloakAdminClient keycloakAdminClient;
    CurrentUserProvider currentUserProvider;
    UserMapper userMapper;
    UserAddressMapper userAddressMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<List<AdminUserResponse>> searchUsers(String keyword, Boolean isActive, Pageable pageable) {
        Page<User> users = userRepository.findAll(matches(keyword, isActive), pageable);
        return PageResponse.<List<AdminUserResponse>>builder()
                .pageNo(pageable.getPageNumber())
                .pageSize(pageable.getPageSize())
                .totalPage(users.getTotalPages())
                .items(users.getContent().stream().map(userMapper::toAdminUserResponse).toList())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUser(String userId) {
        User user = findUser(userId);
        List<UserAddressResponse> addresses = userAddressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId)
                .stream()
                .map(userAddressMapper::toResponse)
                .toList();
        return userMapper.toAdminUserDetailResponse(user, addresses);
    }

    @Override
    @Transactional
    public AdminUserResponse updateUser(String userId, AdminUpdateUserRequest request) {
        User user = findUser(userId);
        userMapper.updateUserFromAdminRequest(request, user);
        return userMapper.toAdminUserResponse(userRepository.save(user));
    }

    /**
     * Keycloak là nơi quyết định đăng nhập được hay không; is_active chỉ ghi sau khi Keycloak đã nhận lệnh.
     * Không có {@code @Transactional}: không giữ transaction DB trong lúc gọi Keycloak.
     */
    @Override
    public AdminUserResponse updateStatus(String userId, boolean active) {
        if (userId.equals(currentUserProvider.getCurrentUserId())) {
            throw new AppException(IdentityErrorCode.CANNOT_CHANGE_OWN_STATUS);
        }
        User user = findUser(userId);

        keycloakAdminClient.setEnabled(userId, active);
        if (!active) {
            // Huỷ mọi phiên + refresh token; Keycloak báo back-channel logout cho BFF xoá session ngay
            keycloakAdminClient.logout(userId);
        }

        user.setIsActive(active);
        log.info("[AdminUser] user {} {} by {}", userId, active ? "unlocked" : "locked", currentUserProvider.getCurrentUserId());
        return userMapper.toAdminUserResponse(userRepository.save(user));
    }

    private User findUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(IdentityErrorCode.USER_NOT_FOUND));
    }

    /** keyword khớp một phần email / họ tên / số điện thoại, không phân biệt hoa thường. */
    private static Specification<User> matches(String keyword, Boolean isActive) {
        Specification<User> spec = (root, query, cb) -> cb.conjunction();
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("email")), pattern),
                    cb.like(cb.lower(root.get("fullName")), pattern),
                    cb.like(root.get("phone"), pattern)));
        }
        if (isActive != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("isActive"), isActive));
        }
        return spec;
    }
}
