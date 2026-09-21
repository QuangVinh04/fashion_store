package com.fashionstore.payment.exception;

import com.fashionstore.common.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum PaymentErrorCode implements BaseErrorCode {
    PAYMENT_NOT_FOUND(5001, "Payment not found", HttpStatus.NOT_FOUND),
    PAYMENT_PROVIDER_UNSUPPORTED(5002, "Payment provider is not supported", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    PAYMENT_SIGNATURE_INVALID(5003, "Payment signature is invalid", HttpStatus.BAD_REQUEST),
    PAYMENT_STATUS_INVALID(5004, "Payment status does not allow this operation", HttpStatus.BAD_REQUEST),
    PAYMENT_PROVIDER_ERROR(5005, "Payment provider request failed", HttpStatus.BAD_GATEWAY),
    PAYMENT_AMOUNT_INVALID(5006, "Payment amount is invalid", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;

    PaymentErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }
}
