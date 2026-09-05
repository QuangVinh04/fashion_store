package com.fashionstore.catalog.exception;

import com.fashionstore.common.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum InventoryErrorCode implements BaseErrorCode {
    INVENTORY_NOT_FOUND   (4050, "Inventory not found for variant",      HttpStatus.NOT_FOUND),
    STOCK_INSUFFICIENT    (4051, "Insufficient stock",                    HttpStatus.BAD_REQUEST),
    ALREADY_RESERVED      (4052, "Stock already reserved for this order", HttpStatus.CONFLICT),
    RESERVATION_NOT_FOUND (4053, "Reservation not found for this order",  HttpStatus.NOT_FOUND),
    INVALID_STOCK_QUANTITY(4054, "Stock quantity cannot be negative",      HttpStatus.BAD_REQUEST);
    ;

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;

    InventoryErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }
}
