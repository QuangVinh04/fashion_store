package com.fashionstore.catalog.review.exception;

import com.fashionstore.common.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/** Mã 1035–1038 giữ nguyên số cũ (trước nằm trong ProductErrorCode) để không phá client. */
@Getter
public enum ReviewErrorCode implements BaseErrorCode {
    REVIEW_NOT_FOUND(1035, "Review not found", HttpStatus.NOT_FOUND),
    REVIEW_ALREADY_EXISTS(1036, "Review already exists for this order", HttpStatus.CONFLICT),
    REVIEW_NOT_ELIGIBLE(1037, "Only customers who purchased and received this product can review", HttpStatus.BAD_REQUEST),
    REVIEW_ACTION_FORBIDDEN(1038, "You can only modify or delete your own review", HttpStatus.FORBIDDEN),
    ;

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;

    ReviewErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }
}
