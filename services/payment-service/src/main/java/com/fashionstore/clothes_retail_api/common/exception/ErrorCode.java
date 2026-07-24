package com.fashionstore.product.common.exception;

import com.fashionstore.common.exception.BaseErrorCode;


import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum ErrorCode implements BaseErrorCode {
    //System

    //Product
    CATEGORY_NOT_FOUND(1001, "category not found", HttpStatus.NOT_FOUND),
    CATEGORY_ALREADY_EXIST(1002, "category already exist", HttpStatus.CONFLICT),
    PRODUCT_NOT_FOUND(1003, "prouct not found", HttpStatus.NOT_FOUND),
    PRODUCT_VARIANT_NOT_FOUND(1004, "product variant not found", HttpStatus.NOT_FOUND),
    STOCK_INSUFFICIENT(1005, "stock is insufficient", HttpStatus.BAD_REQUEST),


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

    // Cart
    CART_ITEM_NOT_FOUND(3001, "Cart item not found", HttpStatus.NOT_FOUND),
    CART_EMPTY(3002, "Cart is empty", HttpStatus.BAD_REQUEST),

    // Order
    ORDER_NOT_FOUND(4001, "Order not found", HttpStatus.NOT_FOUND),
    ORDER_STATUS_INVALID(4002, "Order status transition is invalid", HttpStatus.BAD_REQUEST),
    CHECKOUT_NOT_FOUND(4003, "Checkout not found", HttpStatus.NOT_FOUND),
    CHECKOUT_STATUS_INVALID(4004, "Checkout status does not allow update", HttpStatus.BAD_REQUEST),
    CHECKOUT_AMOUNT_INVALID(4005, "Checkout amount is invalid", HttpStatus.BAD_REQUEST),

    // Payment
    PAYMENT_NOT_FOUND(5001, "Payment not found", HttpStatus.NOT_FOUND),
    PAYMENT_PROVIDER_UNSUPPORTED(5002, "Payment provider is not supported", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    PAYMENT_SIGNATURE_INVALID(5003, "Payment signature is invalid", HttpStatus.BAD_REQUEST),
    PAYMENT_STATUS_INVALID(5004, "Payment status does not allow this operation", HttpStatus.BAD_REQUEST),
    PAYMENT_PROVIDER_ERROR(5005, "Payment provider request failed", HttpStatus.BAD_GATEWAY),
    PAYMENT_AMOUNT_INVALID(5006, "Payment amount is invalid", HttpStatus.BAD_REQUEST),
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
