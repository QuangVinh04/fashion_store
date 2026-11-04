package com.fashionstore.identity.exception;

import com.fashionstore.common.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum IdentityErrorCode implements BaseErrorCode {
    AVATAR_MEDIA_INVALID(2022, "Avatar media is invalid or expired", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND(2001, "User not found", HttpStatus.NOT_FOUND),
    ACCOUNT_DISABLED(2004, "Account is disabled", HttpStatus.FORBIDDEN),
    UNAUTHENTICATED(2005, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    ADDRESS_NOT_FOUND(2016, "Địa chỉ không tồn tại", HttpStatus.NOT_FOUND),
    ACCOUNT_LINK_CONFLICT(2020, "Email đã thuộc về một tài khoản cũ chưa được chuyển sang Keycloak", HttpStatus.CONFLICT),
    CANNOT_CHANGE_OWN_STATUS(2021, "Không thể tự khoá / mở khoá tài khoản của chính mình", HttpStatus.CONFLICT),
    GROUP_NOT_FOUND(2030, "Nhóm không tồn tại", HttpStatus.NOT_FOUND),
    GROUP_NAME_TAKEN(2031, "Tên nhóm đã được dùng", HttpStatus.CONFLICT),
    SYSTEM_GROUP_READ_ONLY(2032, "Nhóm hệ thống không thể sửa hoặc xoá", HttpStatus.CONFLICT),
    PERMISSION_NOT_ASSIGNABLE(2033, "Quyền không hợp lệ hoặc không được gán cho nhóm", HttpStatus.BAD_REQUEST),
    LAST_ADMIN_REQUIRED(2034, "Phải còn ít nhất một quản trị viên", HttpStatus.CONFLICT),
    CANNOT_LEAVE_OWN_ADMIN(2035, "Không thể tự rời nhóm Admin của chính mình", HttpStatus.CONFLICT),
    CUSTOMER_GROUP_NOT_ASSIGNABLE(2036, "Không thể gán nhóm Customer cho nhân viên", HttpStatus.BAD_REQUEST),
    STAFF_EMAIL_TAKEN(2037, "Email đã được dùng cho một tài khoản khác", HttpStatus.CONFLICT),
    STAFF_PASSWORD_REJECTED(2038, "Keycloak từ chối thông tin tài khoản (mật khẩu không đạt chính sách hoặc dữ liệu không hợp lệ)", HttpStatus.BAD_REQUEST),
    STAFF_GROUP_REQUIRED(2039, "Nhân viên phải thuộc ít nhất một nhóm", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;

    IdentityErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }
}
