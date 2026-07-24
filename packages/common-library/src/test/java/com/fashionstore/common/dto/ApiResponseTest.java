package com.fashionstore.common.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiResponseTest {

    @Test
    void builderUsesPlatformSuccessCode() {
        ApiResponse<String> response = ApiResponse.<String>builder()
                .message("ok")
                .data("value")
                .build();

        assertEquals(1000, response.getCode());
        assertEquals("value", response.getData());
    }
}
