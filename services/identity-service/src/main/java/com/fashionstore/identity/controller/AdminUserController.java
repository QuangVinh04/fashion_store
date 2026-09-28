package com.fashionstore.identity.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.identity.dto.user.AdminUpdateUserRequest;
import com.fashionstore.identity.dto.user.AdminUserDetailResponse;
import com.fashionstore.identity.dto.user.AdminUserResponse;
import com.fashionstore.identity.dto.user.UpdateUserStatusRequest;
import com.fashionstore.identity.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Phân quyền ADMIN nằm ở tầng service ({@code @PreAuthorize}). */
@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin Users", description = "Quản lý khách hàng (ADMIN): tra cứu, sửa thông tin cơ bản, khoá / mở khoá")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminUserController {

    AdminUserService adminUserService;

    @Operation(summary = "Danh sách / tìm kiếm người dùng",
            description = "`keyword` khớp một phần email, họ tên hoặc số điện thoại. Mặc định 20 người/trang, mới nhất trước.")
    @GetMapping
    public ApiResponse<PageResponse<List<AdminUserResponse>>> searchUsers(
            @Parameter(description = "Email / họ tên / số điện thoại") @RequestParam(required = false) String keyword,
            @Parameter(description = "Lọc theo trạng thái; bỏ trống là tất cả") @RequestParam(required = false) Boolean isActive,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.<PageResponse<List<AdminUserResponse>>>builder()
                .message("Lấy danh sách người dùng thành công")
                .data(adminUserService.searchUsers(keyword, isActive, pageable))
                .build();
    }

    @Operation(summary = "Chi tiết người dùng kèm sổ địa chỉ",
            description = "Lịch sử đơn lấy qua `GET /api/v1/admin/orders?userId=...`. Mã lỗi: `2001` (404).")
    @GetMapping("/{id}")
    public ApiResponse<AdminUserDetailResponse> getUser(@PathVariable String id) {
        return ApiResponse.<AdminUserDetailResponse>builder()
                .message("Lấy thông tin người dùng thành công")
                .data(adminUserService.getUser(id))
                .build();
    }

    @Operation(summary = "Cập nhật thông tin cơ bản (họ tên, số điện thoại)",
            description = """
                    Thay nguyên: `phone` bỏ trống là xoá số điện thoại. Email do Keycloak quản lý nên không sửa ở đây.
                    Mã lỗi: `2001` (404) · `400` dữ liệu không hợp lệ.""")
    @PutMapping("/{id}")
    public ApiResponse<AdminUserResponse> updateUser(@PathVariable String id,
                                                     @Valid @RequestBody AdminUpdateUserRequest request) {
        return ApiResponse.<AdminUserResponse>builder()
                .message("Cập nhật thông tin người dùng thành công")
                .data(adminUserService.updateUser(id, request))
                .build();
    }

    @Operation(summary = "Khoá / mở khoá tài khoản",
            description = """
                    Khoá: tắt tài khoản ở Keycloak và huỷ mọi phiên — người dùng trên web bị đăng xuất ngay
                    (back-channel logout ở BFF); access token đã cấp cho client gọi thẳng gateway còn tối đa
                    `accessTokenLifespan` (5 phút), riêng API của identity từ chối ngay.
                    Mã lỗi: `2001` (404) · `2021` tự khoá chính mình (409) · `9006` Keycloak không phản hồi (502).""")
    @PatchMapping("/{id}/status")
    public ApiResponse<AdminUserResponse> updateStatus(@PathVariable String id,
                                                       @Valid @RequestBody UpdateUserStatusRequest request) {
        return ApiResponse.<AdminUserResponse>builder()
                .message(request.active() ? "Mở khoá tài khoản thành công" : "Khoá tài khoản thành công")
                .data(adminUserService.updateStatus(id, request.active()))
                .build();
    }
}
