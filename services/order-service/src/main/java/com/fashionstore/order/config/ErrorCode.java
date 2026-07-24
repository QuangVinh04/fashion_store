package com.fashionstore.order.config;

import com.fashionstore.common.exception.BaseErrorCode;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum ErrorCode implements BaseErrorCode {
    UNAUTHENTICATED(2005, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    USER_NOT_FOUND(2001, "User not found", HttpStatus.NOT_FOUND),
    CART_EMPTY(3002, "Cart is empty", HttpStatus.BAD_REQUEST),
    CART_ITEM_NOT_FOUND(3001, "Cart item not found", HttpStatus.NOT_FOUND),
    STOCK_INSUFFICIENT(1005, "stock is insufficient", HttpStatus.BAD_REQUEST),
    ORDER_NOT_FOUND(4001, "Order not found", HttpStatus.NOT_FOUND),
    ORDER_STATUS_INVALID(4002, "Order status transition is invalid", HttpStatus.BAD_REQUEST),
    CHECKOUT_NOT_FOUND(4003, "Checkout not found", HttpStatus.NOT_FOUND),
    CHECKOUT_STATUS_INVALID(4004, "Checkout status does not allow update", HttpStatus.BAD_REQUEST),
    CHECKOUT_AMOUNT_INVALID(4005, "Checkout amount is invalid", HttpStatus.BAD_REQUEST),
    PAYMENT_PROVIDER_UNSUPPORTED(5002, "Payment provider is not supported", HttpStatus.UNSUPPORTED_MEDIA_TYPE);

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;

    ErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }
}
