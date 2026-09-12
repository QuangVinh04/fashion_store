package com.fashionstore.identity.config;

import com.fashionstore.common.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum ErrorCode implements BaseErrorCode {
    USER_NOT_FOUND(2001, "User not found", HttpStatus.NOT_FOUND),
    EMAIL_ALREADY_EXISTS(2002, "Email already exists", HttpStatus.CONFLICT),
    INVALID_CREDENTIALS(2003, "Invalid email or password", HttpStatus.UNAUTHORIZED),
    ACCOUNT_DISABLED(2004, "Account is disabled", HttpStatus.FORBIDDEN),
    UNAUTHENTICATED(2005, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    ROLE_NOT_FOUND(2006, "Role not found", HttpStatus.NOT_FOUND),
    EMAIL_NOT_VERIFIED(2007, "Email not verified", HttpStatus.FORBIDDEN),
    VERIFICATION_TOKEN_EXPIRED(2008, "Verification link has expired", HttpStatus.BAD_REQUEST),
    VERIFICATION_TOKEN_INVALID(2009, "Verification link is invalid", HttpStatus.BAD_REQUEST),
    EMAIL_ALREADY_VERIFIED(2010, "Email is already verified", HttpStatus.CONFLICT);

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;

    ErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }
}
