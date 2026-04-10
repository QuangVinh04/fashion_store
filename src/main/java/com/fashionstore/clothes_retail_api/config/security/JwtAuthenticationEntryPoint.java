package com.fashionstore.clothes_retail_api.config.security;

import com.fashionstore.clothes_retail_api.common.dto.ApiResponse;
import com.fashionstore.clothes_retail_api.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        ErrorCode errorCode = ErrorCode.AUTH_UNAUTHORIZED;

        //Đặt mã HTTP status của response thành 401
        response.setStatus(errorCode.getStatusCode().value());
        //Xác định kiểu phản hồi là JSON.
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        //Tạo đối tượng phản hồi API
        ApiResponse<?> apiResponse = ApiResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();

        //Chuyển đối tượng phản hồi thành JSON và gửi về client
        ObjectMapper objectMapper = new ObjectMapper();
        // Ghi chuỗi JSON vào phản hồi HTTP.
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
        response.flushBuffer(); // Đảm bảo dữ liệu được gửi ngay lập tức.
    }
}
