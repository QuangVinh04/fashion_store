package com.fashionstore.cart.exception;

import com.fashionstore.common.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum CartErrorCode implements BaseErrorCode {
    PRODUCT_VARIANT_NOT_FOUND(1004, "Product variant not found", HttpStatus.NOT_FOUND),
    STOCK_INSUFFICIENT(1005, "Stock is insufficient", HttpStatus.BAD_REQUEST),
    CART_ITEM_NOT_FOUND(3001, "Cart item not found", HttpStatus.NOT_FOUND),
    CART_NOT_ACTIVE(3002, "Cart not active", HttpStatus.FORBIDDEN),
    MULTIPLE_ACTIVE_CARTS(3003, "Multiple active carts", HttpStatus.FORBIDDEN)


    ;

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;

    CartErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }
}
