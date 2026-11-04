package com.fashionstore.gateway.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

// Chỉ kiểm tra route nào khớp path; không gọi backend
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "logging.level.root=WARN")
class AdminRoutingTest {

    @Autowired RouteLocator routeLocator;
    @MockitoBean ReactiveJwtDecoder decoder;

    @ParameterizedTest
    @CsvSource({
            "GET,/api/v1/admin/users/abc,identity-service",
            "GET,/api/v1/admin/permissions,identity-service",
            "GET,/api/v1/admin/groups,identity-service",
            "PUT,/api/v1/admin/groups/g1,identity-service",
            "GET,/api/v1/admin/groups/g1/members,identity-service",
            "POST,/api/v1/admin/staff,identity-service",
            "GET,/api/v1/admin/dashboard/operations,order-service",
            "GET,/api/v1/admin/reviews,catalog-service",
            "GET,/admin/inventory,catalog-service",
            "GET,/admin/size-charts/1,catalog-service",
            "POST,/api/v1/files/evidence/payments/presign,catalog-service",
            "GET,/api/v1/admin/payments/1,payment-service",
            "GET,/api/v1/admin/wallets/1,payment-service"})
    void adminPathResolvesToOwningService(String method, String path, String expectedHost) {
        Route route = firstRoute(HttpMethod.valueOf(method), path);

        assertThat(route).as("route for %s %s", method, path).isNotNull();
        assertThat(route.getUri().getHost()).isEqualTo(expectedHost);
    }

    @Test void unknownAdminPathHasNoRoute() {
        assertThat(firstRoute(HttpMethod.GET, "/api/v1/admin/unknown")).isNull();
    }

    private Route firstRoute(HttpMethod method, String path) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.method(method, path).build());
        return routeLocator.getRoutes()
                .filter(candidate -> Boolean.TRUE.equals(Mono.from(candidate.getPredicate().apply(exchange)).block()))
                .blockFirst();
    }
}
