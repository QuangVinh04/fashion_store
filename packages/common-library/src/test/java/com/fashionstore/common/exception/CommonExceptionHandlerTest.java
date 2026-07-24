package com.fashionstore.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class CommonExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void preservesServiceOwnedBusinessError() {
        MockHttpServletRequest request = request();

        ResponseEntity<ApiErrorResponse> response = handler.handleAppException(
                new AppException(TestErrorCode.CART_EMPTY),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .extracting(
                        ApiErrorResponse::code,
                        ApiErrorResponse::status,
                        ApiErrorResponse::message,
                        ApiErrorResponse::path,
                        ApiErrorResponse::correlationId
                )
                .containsExactly(
                        3002,
                        400,
                        "Cart is empty",
                        "/api/v1/cart/checkout",
                        "test-correlation-id"
                );
    }

    @Test
    void doesNotExposeCustomInternalMessage() {
        MockHttpServletRequest request = request();

        ResponseEntity<ApiErrorResponse> response = handler.handleAppException(
                new AppException(
                        ErrorCode.UPSTREAM_SERVICE_ERROR,
                        "Connection refused at internal-host:8080"
                ),
                request
        );

        assertThat(response.getBody().message())
                .isEqualTo(ErrorCode.UPSTREAM_SERVICE_ERROR.getMessage())
                .doesNotContain("internal-host");
    }

    @Test
    void mapsUnexpectedExceptionToSafeCommonError() {
        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpectedException(
                new IllegalStateException("database password was invalid"),
                request()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.INTERNAL_ERROR.getCode());
        assertThat(response.getBody().message())
                .isEqualTo(ErrorCode.INTERNAL_ERROR.getMessage())
                .doesNotContain("password");
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/cart/checkout");
        request.addHeader("X-Correlation-Id", "test-correlation-id");
        return request;
    }

    private enum TestErrorCode implements BaseErrorCode {
        CART_EMPTY(3002, "Cart is empty", HttpStatus.BAD_REQUEST);

        private final int code;
        private final String message;
        private final HttpStatusCode statusCode;

        TestErrorCode(int code, String message, HttpStatusCode statusCode) {
            this.code = code;
            this.message = message;
            this.statusCode = statusCode;
        }

        @Override
        public int getCode() {
            return code;
        }

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public HttpStatusCode getStatusCode() {
            return statusCode;
        }
    }
}
