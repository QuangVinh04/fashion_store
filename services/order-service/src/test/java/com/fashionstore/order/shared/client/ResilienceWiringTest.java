package com.fashionstore.order.shared.client;

import com.fashionstore.order.shipment.client.GhnApi;
import com.fashionstore.order.shipment.client.GhnFeignClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.order.shipment.dto.GhnCreateOrderRequest;
import com.fashionstore.order.shared.exception.OrderErrorCode;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;

/** Chạy Resilience4j thật với application.yaml thật: breaker/retry/predicate đúng như khi triển khai. */
@SpringBootTest(classes = ResilienceWiringTest.App.class)
class ResilienceWiringTest {

    @Configuration
    @ImportAutoConfiguration({AopAutoConfiguration.class, CircuitBreakerAutoConfiguration.class, RetryAutoConfiguration.class})
    @Import({IdentityClient.class, GhnApi.class, CatalogClient.class})
    static class App {}

    @MockitoBean IdentityFeignClient identityFeign;
    @MockitoBean GhnFeignClient ghnFeign;
    @MockitoBean CatalogFeignClient catalogFeign;

    @Autowired IdentityClient identity;
    @Autowired GhnApi ghn;
    @Autowired CatalogClient catalog;
    @Autowired CircuitBreakerRegistry breakers;

    @BeforeEach
    void resetState() {
        breakers.getAllCircuitBreakers().forEach(io.github.resilience4j.circuitbreaker.CircuitBreaker::reset);
        reset(identityFeign, ghnFeign, catalogFeign);
    }

    private static Request request() {
        return Request.create(Request.HttpMethod.GET, "/x", Collections.emptyMap(), null, StandardCharsets.UTF_8,
                new RequestTemplate());
    }

    private static FeignException unavailable() {
        return new FeignException.ServiceUnavailable("down", request(), null, null);
    }

    @Test
    void identityDownIsRetriedThenSurfacesAsBadGateway() {
        when(identityFeign.getAddressOfUser("u", "a")).thenThrow(unavailable());

        assertThatThrownBy(() -> identity.getAddress("u", "a"))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UPSTREAM_SERVICE_ERROR));
        verify(identityFeign, times(3)).getAddressOfUser("u", "a");
    }

    @Test
    void aBusinessNotFoundIsNeitherRetriedNorCountedAgainstTheCircuit() {
        when(identityFeign.getAddressOfUser("u", "a"))
                .thenThrow(new FeignException.NotFound("nf", request(), null, null));

        for (int i = 0; i < 12; i++) {
            assertThatThrownBy(() -> identity.getAddress("u", "a"))
                    .isInstanceOfSatisfying(AppException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(OrderErrorCode.ADDRESS_NOT_FOUND));
        }
        verify(identityFeign, times(12)).getAddressOfUser("u", "a");
        assertThat(breakers.circuitBreaker("identityService").getState())
                .isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED);
    }

    @Test
    void whenIdentityKeepsFailingTheCircuitOpensAndCallsStopReachingIt() {
        when(identityFeign.getUserById("u")).thenThrow(unavailable());

        for (int i = 0; i < 5; i++) {
            assertThat(identity.getUser("u")).isNull(); // fallback: thông tin phụ, không có thì thôi
        }
        assertThat(breakers.circuitBreaker("identityService").getState())
                .isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN);

        reset(identityFeign);
        assertThat(identity.getUser("u")).isNull();
        verify(identityFeign, never()).getUserById(anyString());
        assertThatThrownBy(() -> identity.getAddress("u", "a"))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UPSTREAM_SERVICE_ERROR));
        verify(identityFeign, never()).getAddressOfUser(anyString(), anyString());
    }

    @Test
    void ghnReadsAreRetriedButCreatingAShipmentIsAttemptedOnlyOnce() {
        when(ghnFeign.getOrderDetail("t", "1", "code")).thenThrow(unavailable());
        when(ghnFeign.createOrder(anyString(), anyString(), any())).thenThrow(unavailable());

        assertThatThrownBy(() -> ghn.getOrderDetail("t", "1", "code")).isInstanceOf(AppException.class);
        verify(ghnFeign, times(3)).getOrderDetail("t", "1", "code");

        assertThatThrownBy(() -> ghn.createOrder("t", "1", GhnCreateOrderRequest.builder().build()))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UPSTREAM_SERVICE_ERROR));
        verify(ghnFeign, times(1)).createOrder(anyString(), anyString(), any());
    }

    @Test
    void ghnClientErrorsLikeABadAddressDoNotOpenTheCircuit() {
        when(ghnFeign.previewOrder(anyString(), anyString(), any()))
                .thenThrow(new FeignException.BadRequest("bad address", request(), null, null));

        for (int i = 0; i < 12; i++) {
            assertThatThrownBy(() -> ghn.previewOrder("t", "1", GhnCreateOrderRequest.builder().build()))
                    .isInstanceOf(FeignException.BadRequest.class);
        }
        verify(ghnFeign, times(12)).previewOrder(anyString(), anyString(), any());
        assertThat(breakers.circuitBreaker("ghnService").getState())
                .isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED);
    }

    @Test
    void returnEvidenceLookupsAreRetriedAndMappedToBadGatewayButRestockIsNot() {
        when(catalogFeign.getEvidenceReadUrl("m", "o")).thenThrow(unavailable());
        when(catalogFeign.restock("order-1")).thenThrow(unavailable());

        assertThatThrownBy(() -> catalog.getEvidenceReadUrl("m", "o"))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UPSTREAM_SERVICE_ERROR));
        verify(catalogFeign, times(3)).getEvidenceReadUrl("m", "o");

        catalog.restock("order-1"); // fallback chỉ log
        verify(catalogFeign, times(1)).restock("order-1");
    }
}
