package com.fashionstore.payment.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.payment.exception.PaymentErrorCode;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
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
    @Import(VnPayApi.class)
    static class App {}

    @MockitoBean VnPayFeignClient vnPayFeign;

    @Autowired VnPayApi vnPay;
    @Autowired CircuitBreakerRegistry breakers;

    @BeforeEach
    void resetState() {
        breakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        reset(vnPayFeign);
    }

    private static FeignException unavailable() {
        return new FeignException.ServiceUnavailable("down",
                Request.create(Request.HttpMethod.POST, "/x", Collections.emptyMap(), null, StandardCharsets.UTF_8,
                        new RequestTemplate()), null, null);
    }

    private static void assertProviderError(Throwable e) {
        assertThat(e).isInstanceOfSatisfying(AppException.class,
                a -> assertThat(a.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_PROVIDER_ERROR));
    }

    @Test
    void queryDrIsRetriedThenReportedAsProviderError() {
        when(vnPayFeign.query(any())).thenThrow(unavailable());

        assertThatThrownBy(() -> vnPay.query(Map.of("a", "b"))).satisfies(ResilienceWiringTest::assertProviderError);
        verify(vnPayFeign, times(3)).query(any());
    }

    @Test
    void whenVnPayKeepsFailingTheCircuitOpensAndCallsStopReachingIt() {
        when(vnPayFeign.query(any())).thenThrow(unavailable());

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> vnPay.query(Map.of())).satisfies(ResilienceWiringTest::assertProviderError);
        }
        assertThat(breakers.circuitBreaker("vnpayService").getState()).isEqualTo(CircuitBreaker.State.OPEN);

        reset(vnPayFeign);
        assertThatThrownBy(() -> vnPay.query(Map.of())).satisfies(ResilienceWiringTest::assertProviderError);
        verify(vnPayFeign, never()).query(any());
    }
}
