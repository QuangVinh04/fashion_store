package com.fashionstore.catalog.shared.client;

import com.fashionstore.catalog.media.client.IdentityAvatarClient;
import com.fashionstore.catalog.media.client.IdentityAvatarFeignClient;
import com.fashionstore.catalog.review.client.OrderClient;
import com.fashionstore.catalog.review.client.OrderFeignClient;
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
import com.fashionstore.contracts.order.dto.VerifyPurchaseRequest;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
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

/** Chạy Resilience4j thật với application.yaml thật: breaker/retry/predicate đúng như khi triển khai. */
@SpringBootTest(classes = ResilienceWiringTest.App.class)
class ResilienceWiringTest {

    @Configuration
    @ImportAutoConfiguration({AopAutoConfiguration.class, CircuitBreakerAutoConfiguration.class, RetryAutoConfiguration.class})
    @Import({OrderClient.class, IdentityAvatarClient.class})
    static class App {}

    @MockitoBean OrderFeignClient orderFeign;
    @MockitoBean IdentityAvatarFeignClient avatarFeign;

    @Autowired OrderClient orders;
    @Autowired IdentityAvatarClient avatars;
    @Autowired CircuitBreakerRegistry breakers;

    private static final VerifyPurchaseRequest VERIFY = new VerifyPurchaseRequest("u", "o", java.util.List.of("v"));

    @BeforeEach
    void resetState() {
        breakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        reset(orderFeign, avatarFeign);
    }

    private static Request request() {
        return Request.create(Request.HttpMethod.POST, "/x", Collections.emptyMap(), null, StandardCharsets.UTF_8,
                new RequestTemplate());
    }

    private static FeignException unavailable() {
        return new FeignException.ServiceUnavailable("down", request(), null, null);
    }

    private static void assertBadGateway(Throwable e) {
        assertThat(e).isInstanceOfSatisfying(AppException.class,
                a -> assertThat(a.getErrorCode()).isEqualTo(ErrorCode.UPSTREAM_SERVICE_ERROR));
    }

    @Test
    void orderDownIsRetriedThenSurfacesAsBadGatewayNeverAsNotPurchased() {
        when(orderFeign.verifyPurchase(any())).thenThrow(unavailable());

        assertThatThrownBy(() -> orders.verifyPurchase(VERIFY)).satisfies(ResilienceWiringTest::assertBadGateway);
        verify(orderFeign, times(3)).verifyPurchase(any());
    }

    @Test
    void aClientErrorIsNotRetriedAndNeverOpensTheCircuit() {
        when(orderFeign.verifyPurchase(any())).thenThrow(new FeignException.BadRequest("bad", request(), null, null));

        for (int i = 0; i < 12; i++) {
            assertThatThrownBy(() -> orders.verifyPurchase(VERIFY)).isInstanceOf(FeignException.BadRequest.class);
        }
        verify(orderFeign, times(12)).verifyPurchase(any());
        assertThat(breakers.circuitBreaker("orderService").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void whenIdentityKeepsFailingTheCircuitOpensAndCallsStopReachingIt() {
        when(avatarFeign.getAvatarReference("u")).thenThrow(unavailable());

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> avatars.getAvatarReference("u")).satisfies(ResilienceWiringTest::assertBadGateway);
        }
        assertThat(breakers.circuitBreaker("identityService").getState()).isEqualTo(CircuitBreaker.State.OPEN);

        reset(avatarFeign);
        assertThatThrownBy(() -> avatars.getAvatarReference("u")).satisfies(ResilienceWiringTest::assertBadGateway);
        verify(avatarFeign, never()).getAvatarReference(anyString());
    }
}
