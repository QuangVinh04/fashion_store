package com.fashionstore.identity.service.impl;

import static com.fashionstore.identity.service.impl.PreAuthorizeExpressions.CUSTOMER_MANAGE;
import static com.fashionstore.identity.service.impl.PreAuthorizeExpressions.CUSTOMER_READ;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.security.PermissionNames;
import com.fashionstore.identity.client.KeycloakAdminClient;
import com.fashionstore.identity.client.KeycloakGroupClient;
import com.fashionstore.identity.dto.group.GroupResponse;
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
import com.fashionstore.identity.service.GroupService;
import com.fashionstore.identity.service.SystemGroups;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminUserServiceImpl implements AdminUserService {

    private static final String USER_ENTITY = "USER";
    private static final String ADMIN_ACCOUNT_EDIT_DENIED =
            "Chỉ người có quyền quản lý nhóm mới được sửa tài khoản quản trị viên";

    UserRepository userRepository;
    UserAddressRepository userAddressRepository;
    KeycloakAdminClient keycloakAdminClient;
    CurrentUserProvider currentUserProvider;
    UserMapper userMapper;
    UserAddressMapper userAddressMapper;
    GroupService groupService;
    KeycloakGroupClient groupClient;
    IdentityAuditRecorder auditRecorder;

    @Override
    @PreAuthorize(CUSTOMER_READ)
    @Transactional(readOnly = true)
    public PageResponse<List<AdminUserResponse>> searchUsers(String keyword, Boolean isActive, Pageable pageable) {
        Page<User> users = userRepository.findAll(matches(keyword, isActive), pageable);
        return PageResponse.<List<AdminUserResponse>>builder()
                .pageNo(pageable.getPageNumber())
                .pageSize(pageable.getPageSize())
                .totalPage(users.getTotalPages())
                .totalElements(users.getTotalElements())
                .items(users.getContent().stream().map(userMapper::toAdminUserResponse).toList())
                .build();
    }

    @Override
    @PreAuthorize(CUSTOMER_READ)
    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUser(String userId) {
        User user = findUser(userId);
        List<UserAddressResponse> addresses = userAddressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId)
                .stream()
                .map(userAddressMapper::toResponse)
                .toList();
        return userMapper.toAdminUserDetailResponse(user, addresses, groupsOrEmpty(userId));
    }

    // Hồ sơ và địa chỉ lấy từ identity DB, phải đọc được cả khi Keycloak không khả dụng.
    private List<GroupResponse> groupsOrEmpty(String userId) {
        try {
            return groupService.getUserGroups(userId);
        } catch (AppException groupsFailure) {
            log.warn("[AdminUser] groups unavailable for user {}: {}", userId, groupsFailure.getMessage());
            return List.of();
        }
    }

    @Override
    @PreAuthorize(CUSTOMER_MANAGE)
    public AdminUserResponse updateUser(String userId, AdminUpdateUserRequest request) {
        User user = findUser(userId);
        requireGroupManageForAdminTarget(userId);
        userMapper.updateUserFromAdminRequest(request, user);
        return userMapper.toAdminUserResponse(userRepository.save(user));
    }

    /**
     * Keycloak là nơi quyết định đăng nhập được hay không; is_active chỉ ghi sau khi Keycloak đã nhận lệnh.
     * Không có {@code @Transactional}: không giữ transaction DB trong lúc gọi Keycloak.
     */
    @Override
    @PreAuthorize(CUSTOMER_MANAGE)
    public AdminUserResponse updateStatus(String userId, boolean active) {
        if (userId.equals(currentUserProvider.getCurrentUserId())) {
            throw new AppException(IdentityErrorCode.CANNOT_CHANGE_OWN_STATUS);
        }
        User user = findUser(userId);
        requireGroupManageForAdminTarget(userId);

        keycloakAdminClient.setEnabled(userId, active);
        if (!active) {
            // Huỷ mọi phiên + refresh token; Keycloak báo back-channel logout cho BFF xoá session ngay
            keycloakAdminClient.logout(userId);
        }

        boolean isChanged = !Boolean.valueOf(active).equals(user.getIsActive());
        user.setIsActive(active);
        log.info("[AdminUser] user {} {} by {}", userId, active ? "unlocked" : "locked", currentUserProvider.getCurrentUserId());
        AdminUserResponse response = userMapper.toAdminUserResponse(userRepository.save(user));
        if (isChanged) {
            auditRecorder.record(active ? IdentityAuditRecorder.USER_UNLOCK : IdentityAuditRecorder.USER_LOCK,
                    USER_ENTITY, userId, active ? "Mở khoá tài khoản" : "Khoá tài khoản");
        }
        return response;
    }

    /**
     * customer:manage gán được cho nhóm tuỳ chỉnh nên không đủ để đụng tới tài khoản Admin (nếu không, nhóm đó khoá
     * được mọi admin, vượt qua cả rào "còn ít nhất một admin"). Keycloak lỗi thì lỗi lan ra: không biết đích thuộc
     * nhóm nào thì không cho ghi.
     */
    private void requireGroupManageForAdminTarget(String userId) {
        if (callerHasAuthority(PermissionNames.GROUP_MANAGE)) {
            return;
        }
        if (isInAdminGroup(userId)) {
            throw new AccessDeniedException(ADMIN_ACCOUNT_EDIT_DENIED);
        }
    }

    // User chỉ có trong identity DB (chưa/không còn ở Keycloak) thì không thể là admin.
    private boolean isInAdminGroup(String userId) {
        try {
            return groupClient.listUserGroups(userId).stream()
                    .anyMatch(group -> SystemGroups.ADMIN.equals(group.name()));
        } catch (AppException lookupFailure) {
            if (lookupFailure.getErrorCode() == IdentityErrorCode.USER_NOT_FOUND) {
                return false;
            }
            throw lookupFailure;
        }
    }

    private static boolean callerHasAuthority(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
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
