package com.fashionstore.catalog.exception;

import com.fashionstore.common.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum FileErrorCode implements BaseErrorCode {
    FILE_NOT_FOUND(6001, "File not found", HttpStatus.NOT_FOUND),
    FILE_UPLOAD_INVALID(6002, "File upload is invalid", HttpStatus.BAD_REQUEST),
    FILE_STORAGE_FAILED(6003, "File storage failed", HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_ACCESS_DENIED(6004, "File access denied", HttpStatus.FORBIDDEN),
    FILE_ALREADY_TRASHED(6005, "File already trashed", HttpStatus.BAD_REQUEST),
    FILE_NOT_TRASHED(6006, "File is not trashed", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;

    FileErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }
}
