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
