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
    CANNOT_CHANGE_OWN_STATUS(2021, "Không thể tự khoá / mở khoá tài khoản của chính mình", HttpStatus.CONFLICT);

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;

    IdentityErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }
}
