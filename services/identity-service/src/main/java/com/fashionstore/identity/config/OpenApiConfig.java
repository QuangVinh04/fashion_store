package com.fashionstore.identity.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI identityServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Identity Service API")
                        .version("v1")
                        .description("Xác thực, quản lý hồ sơ người dùng, địa chỉ giao hàng và JWKS."))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token do Identity Service phát hành")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
