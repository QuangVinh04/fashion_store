package com.fashionstore.payment.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI paymentServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Payment Service API")
                        .version("v1")
                        .description("Khởi tạo thanh toán, tra cứu payment và xử lý callback từ VNPay/PayOS."))
                .servers(List.of(
                        new Server().url("/").description("Current request origin (API Gateway)"),
                        new Server().url("http://localhost:8080").description("API Gateway"),
                        new Server().url("http://localhost:8085").description("Direct service port (Local)")
                ))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token của chủ payment")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
