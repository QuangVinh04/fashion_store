package com.fashionstore.identity.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.identity.dto.staff.CreateStaffRequest;
import com.fashionstore.identity.dto.staff.StaffResponse;
import com.fashionstore.identity.service.StaffAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Yêu cầu {@code group:manage}; quyền nằm ở tầng service ({@code @PreAuthorize}). */
@RestController
@RequestMapping("/api/v1/admin/staff")
@Tag(name = "Admin Staff", description = "Tài khoản nhân viên backoffice do quản trị viên tạo")
@RequiredArgsConstructor
public class AdminStaffController {

    private final StaffAccountService staffAccountService;

    @Operation(summary = "Tạo tài khoản nhân viên",
            description = """
                    Tạo user Keycloak (email đã xác minh, mật khẩu tạm buộc đổi ở lần đăng nhập đầu), thêm vào các
                    nhóm đã chọn và nhóm `Customer`. Không bao giờ trả lại mật khẩu.
                    Mã lỗi: `2020` email còn thuộc tài khoản cũ (409) · `2037` email đã dùng ở Keycloak (409) ·
                    `2038` Keycloak từ chối thông tin tài khoản, thường do mật khẩu không đạt chính sách (400) · `2039` thiếu nhóm (400) · `2036` chứa `Customer` (400) ·
                    `2030` nhóm không tồn tại (404).""")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StaffResponse> createStaff(@Valid @RequestBody CreateStaffRequest request) {
        return ApiResponse.<StaffResponse>builder()
                .message("Tạo tài khoản nhân viên thành công")
                .data(staffAccountService.createStaff(request))
                .build();
    }
}
