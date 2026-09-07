package com.fashionstore.order.config;

import com.fashionstore.order.controller.AdminOrderController;
import com.fashionstore.order.controller.CartController;
import com.fashionstore.order.controller.CheckoutController;
import com.fashionstore.order.controller.OrderController;
import com.fashionstore.order.service.CartService;
import com.fashionstore.order.service.CheckoutService;
import com.fashionstore.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.configuration.SpringDocPageableConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sinh thật tài liệu OpenAPI từ 4 controller và kiểm chứng những gì thường im lặng sai: thiếu security
 * scheme (Swagger UI không gọi được endpoint nào), Pageable không được làm phẳng, thiếu 401/403.
 */
@WebMvcTest(controllers = {
        CartController.class,
        CheckoutController.class,
        OrderController.class,
        AdminOrderController.class
})
// Chuỗi security thật không được nạp ở slice này (nó cần JWT decoder + issuer), mà test này kiểm tài liệu
// chứ không kiểm phân quyền — nên tắt filter để đọc được /v3/api-docs.
@AutoConfigureMockMvc(addFilters = false)
// Context không có @ComponentScan (cố ý), nên controller phải được đăng ký tường minh.
@Import({
        OpenApiConfig.class,
        CartController.class,
        CheckoutController.class,
        OrderController.class,
        AdminOrderController.class
})
@ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        SpringDocConfiguration.class,
        SpringDocConfigProperties.class,
        SpringDocPageableConfiguration.class,
        SpringDocWebMvcConfiguration.class,
        SwaggerUiConfigProperties.class,
        SwaggerUiOAuthProperties.class
})
class OpenApiDocumentTest {

    /**
     * Context riêng, không dùng {@code OrderServiceApplication}: các {@code @Enable*} trên đó (outbox
     * processed-message, JPA auditing, Feign) đòi DB và broker, chẳng liên quan gì đến việc sinh tài liệu.
     */
    @SpringBootConfiguration
    static class DocumentationOnlyApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private CheckoutService checkoutService;

    @MockitoBean
    private OrderService orderService;

    @Test
    void publishesTheDocumentWithBearerAuthAndAllFourTags() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Order Service API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.tags[*].name")
                        .value(org.hamcrest.Matchers.containsInAnyOrder(
                                "Cart", "Checkout", "Order", "Admin - Orders")));
    }

    @Test
    void documentsEveryEndpointOfTheFourControllers() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/cart'].get.summary").value("Lấy giỏ hàng hiện tại"))
                .andExpect(jsonPath("$.paths['/api/v1/cart/items'].post.summary").exists())
                .andExpect(jsonPath("$.paths['/api/v1/cart/items/{id}'].put.summary").exists())
                .andExpect(jsonPath("$.paths['/api/v1/checkouts'].post.summary").exists())
                .andExpect(jsonPath("$.paths['/api/v1/checkouts/{id}/cancel'].post.summary").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{checkoutId}'].post.summary").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{id}/status'].put.summary").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/orders/{orderId}/saga'].get.summary").exists());
    }

    /** Idempotency-Key là header dễ bị bỏ quên nhất trong tài liệu, mà thiếu nó là tạo trùng đơn. */
    @Test
    void documentsTheIdempotencyKeyHeader() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/orders/{checkoutId}'].post.parameters[?(@.name=='Idempotency-Key')].in")
                        .value("header"));
    }

    /** Không có @ParameterObject thì Pageable hiện ra như một object lồng, Swagger UI không gọi được. */
    @Test
    void flattensPageableIntoQueryParameters() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/orders'].get.parameters[*].name")
                        .value(org.hamcrest.Matchers.hasItems("page", "size", "sort", "status")));
    }

    @Test
    void addsAuthFailureResponsesToEveryOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/cart'].get.responses.401").exists())
                .andExpect(jsonPath("$.paths['/api/v1/cart'].get.responses.403").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/orders'].get.responses.403.content['application/json'].schema.$ref")
                        .value("#/components/schemas/ApiErrorResponse"))
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.correlationId").exists());
    }
}
