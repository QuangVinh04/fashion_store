package com.fashionstore.order.exception;

import com.fashionstore.common.exception.BaseErrorCode;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Mã lỗi nghiệp vụ của order-service, gồm cả phần giỏ hàng đã gộp vào. Lỗi ở tầng framework
 * (unauthorized, validation, upstream, ...) dùng {@link com.fashionstore.common.exception.ErrorCode}.
 */
@Getter
public enum OrderErrorCode implements BaseErrorCode {
    PRODUCT_VARIANT_NOT_FOUND(1004, "Product variant not found", HttpStatus.NOT_FOUND),
    STOCK_INSUFFICIENT(1005, "Stock is insufficient", HttpStatus.BAD_REQUEST),
    PRODUCT_VARIANT_INACTIVE(1006, "Product variant is no longer on sale", HttpStatus.CONFLICT),
    CART_ITEM_NOT_FOUND(3001, "Cart item not found", HttpStatus.NOT_FOUND),
    CART_EMPTY(3002, "Cart is empty", HttpStatus.BAD_REQUEST),
    CART_ITEM_STALE(3003, "Cart item is missing its product snapshot, please add it again", HttpStatus.CONFLICT),
    CART_NOT_ACTIVE(3004, "Cart not active", HttpStatus.FORBIDDEN),
    ORDER_NOT_FOUND(4001, "Order not found", HttpStatus.NOT_FOUND),
    ORDER_STATUS_INVALID(4002, "Order status transition is invalid", HttpStatus.BAD_REQUEST),
    CHECKOUT_NOT_FOUND(4003, "Checkout not found", HttpStatus.NOT_FOUND),
    CHECKOUT_STATUS_INVALID(4004, "Checkout status does not allow update", HttpStatus.BAD_REQUEST),
    CHECKOUT_AMOUNT_INVALID(4005, "Checkout amount is invalid", HttpStatus.BAD_REQUEST),
    ORDER_CANNOT_BE_CANCELLED(4006, "Order can no longer be cancelled by the customer", HttpStatus.CONFLICT),
    ORDER_SAGA_NOT_FOUND(4007, "Saga not found for this order", HttpStatus.NOT_FOUND),
    ORDER_RETURN_NOT_ALLOWED(4008, "Order is not eligible for a return request", HttpStatus.CONFLICT),
    ADDRESS_NOT_FOUND(4009, "Address not found", HttpStatus.NOT_FOUND),
    PROMOTION_NOT_FOUND(4010, "Promotion not found", HttpStatus.NOT_FOUND),
    PROMOTION_EXPIRED(4011, "Promotion is expired or not yet active", HttpStatus.BAD_REQUEST),
    PROMOTION_QUOTA_EXCEEDED(4012, "Promotion quota has been exceeded", HttpStatus.BAD_REQUEST),
    PROMOTION_MIN_ORDER_NOT_MET(4013, "Minimum order amount not met for promotion", HttpStatus.BAD_REQUEST),
    PROMOTION_SCOPE_MISMATCH(4014, "Promotion does not apply to items in order", HttpStatus.BAD_REQUEST),
    PROMOTION_CODE_EXISTS(4015, "Promotion code already exists", HttpStatus.CONFLICT),
    PROMOTION_CANNOT_BE_MODIFIED(4016, "Promotion code cannot be changed after usage has occurred", HttpStatus.BAD_REQUEST),
    PAYMENT_PROVIDER_UNSUPPORTED(5002, "Payment provider is not supported", HttpStatus.UNSUPPORTED_MEDIA_TYPE);

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;

    OrderErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }
}
