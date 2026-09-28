package com.fashionstore.identity.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Thông tin định danh cơ bản admin được sửa. Email do Keycloak quản lý (đồng bộ từ token mỗi lần đăng nhập)
 * nên không nằm ở đây. PUT thay nguyên: phone null là xoá số điện thoại.
 */
public record AdminUpdateUserRequest(
        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
        String fullName,

        @Pattern(regexp = "^(0|\\+84)[0-9]{9}$", message = "Số điện thoại không hợp lệ")
        String phone
) {
}
