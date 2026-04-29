package com.fashionstore.clothes_retail_api.common.exception;


import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum ErrorCode {
    //System
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized exception", HttpStatus.INTERNAL_SERVER_ERROR), // 500
    VALIDATION_REQUIRED_FIELD(9001, "validation required field", HttpStatus.BAD_REQUEST),

    //Product
    CATEGORY_NOT_FOUND(1001, "category not found", HttpStatus.NOT_FOUND),
    CATEGORY_ALREADY_EXIST(1002, "category already exist", HttpStatus.CONFLICT),
    PRODUCT_NOT_FOUND(1003, "prouct not found", HttpStatus.NOT_FOUND),
    PRODUCT_VARIANT_NOT_FOUND(1004, "product variant not found", HttpStatus.NOT_FOUND),


    // Auth
    USER_NOT_FOUND(2001, "User not found", HttpStatus.NOT_FOUND),
    EMAIL_ALREADY_EXISTS(2002, "Email already exists", HttpStatus.CONFLICT),
    INVALID_CREDENTIALS(2003, "Invalid email or password", HttpStatus.UNAUTHORIZED),
    ACCOUNT_DISABLED(2004, "Account is disabled", HttpStatus.FORBIDDEN),
    UNAUTHENTICATED(2005, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    ROLE_NOT_FOUND(2006, "Role not found", HttpStatus.NOT_FOUND),
    EMAIL_NOT_VERIFIED(2007, "Email not verified", HttpStatus.FORBIDDEN),
    VERIFICATION_TOKEN_EXPIRED(2008, "Link xác nhận đã hết hạn", HttpStatus.BAD_REQUEST),
    VERIFICATION_TOKEN_INVALID(2009, "Link xác nhận không hợp lệ", HttpStatus.BAD_REQUEST),
    ;

    private int code;
    private String message;
    private HttpStatusCode statusCode;

    ErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }
}
