package com.fashionstore.order.config;

import com.fashionstore.common.exception.ApiErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    static final String BEARER_SCHEME = "bearerAuth";
    static final String ERROR_SCHEMA = "ApiErrorResponse";

    @Bean
    public OpenAPI orderServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Order Service API")
                        .version("v1")
                        .description("""
                                Giỏ hàng, checkout và đơn hàng — một deployable sau khi cart-service được gộp vào.

                                Luồng đặt hàng: `POST /api/v1/cart/items` → `POST /api/v1/checkouts` (chụp giá, \
                                hạn 30 phút) → `POST /api/v1/orders/{checkoutId}` (mở saga giữ kho + thanh toán). \
                                Đơn ở `PENDING` cho tới khi saga đóng, nên response của bước tạo đơn là "đã nhận", \
                                không phải "đã xác nhận".

                                Mọi response bọc trong `{ code, message, data }`; lỗi trả `ApiErrorResponse` với \
                                `code` là mã nghiệp vụ (xem mô tả từng endpoint) còn HTTP status là ngữ nghĩa vận chuyển."""))
                // Cổng service đứng trước vì Swagger UI được phục vụ từ chính nó — "Try it out" gọi cùng
                // origin nên chạy được ngay. Gateway là đường đi thật của client, nhưng gọi từ UI này sang
                // 8080 là cross-origin và gateway hiện chưa khai báo CORS cho nó.
                .servers(List.of(
                        new Server().url("http://localhost:8089").description("Cổng service — dùng để thử trực tiếp từ trang này"),
                        new Server().url("http://localhost:8080").description("API gateway — đường đi thật của client")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token do identity-service phát hành")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    /**
     * {@code SecurityConfig} đòi authenticated cho mọi request, nên 401/403 là câu trả lời khả dĩ của mọi
     * endpoint — khai báo một lần ở đây thay vì dán annotation lên từng method.
     */
    @Bean
    public OpenApiCustomizer authErrorResponsesCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            // springdoc dựng lại components.schemas từ những gì nó quét được, nên schema lỗi phải đăng ký
            // ở đây — đăng ký trong bean OpenAPI thì bị ghi đè và $ref bên dưới thành ref treo.
            if (openApi.getComponents() != null) {
                openApi.getComponents().addSchemas(ERROR_SCHEMA, errorSchema());
            }
            openApi.getPaths().values().forEach(pathItem -> pathItem.readOperations().forEach(operation -> {
                if (operation.getResponses() == null) {
                    return;
                }
                operation.getResponses()
                        .addApiResponse("401", errorResponse("Thiếu hoặc sai access token"))
                        .addApiResponse("403", errorResponse("Token hợp lệ nhưng không đủ quyền"));
            }));
        };
    }

    private static io.swagger.v3.oas.models.responses.ApiResponse errorResponse(String description) {
        return new io.swagger.v3.oas.models.responses.ApiResponse()
                .description(description)
                .content(new Content().addMediaType(
                        org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA))));
    }

    private static Schema<?> errorSchema() {
        return ModelConverters.getInstance().read(ApiErrorResponse.class).get(ERROR_SCHEMA);
    }
}
