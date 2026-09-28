package com.fashionstore.identity.service;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.identity.dto.user.AdminUpdateUserRequest;
import com.fashionstore.identity.dto.user.AdminUserDetailResponse;
import com.fashionstore.identity.dto.user.AdminUserResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AdminUserService {

    PageResponse<List<AdminUserResponse>> searchUsers(String keyword, Boolean isActive, Pageable pageable);

    AdminUserDetailResponse getUser(String userId);

    AdminUserResponse updateUser(String userId, AdminUpdateUserRequest request);

    AdminUserResponse updateStatus(String userId, boolean active);
}
