package com.fashionstore.catalog.config;

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
    OpenAPI catalogServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Catalog Service API")
                        .version("v1")
                        .description("Sản phẩm, danh mục, thương hiệu, thuộc tính, media, wishlist, review và tồn kho."))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token; một số API quản trị yêu cầu role ADMIN")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
