package com.fashionstore.identity.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.identity.exception.IdentityErrorCode;
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
    @Import({KeycloakAdminClient.class, CatalogMediaClient.class})
    static class App {}

    @MockitoBean KeycloakAdminFeignClient keycloakFeign;
    @MockitoBean CatalogMediaFeignClient mediaFeign;

    @Autowired KeycloakAdminClient keycloak;
    @Autowired CatalogMediaClient media;
    @Autowired CircuitBreakerRegistry breakers;

    @BeforeEach
    void resetState() {
        breakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        reset(keycloakFeign, mediaFeign);
    }

    private static Request request() {
        return Request.create(Request.HttpMethod.PUT, "/x", Collections.emptyMap(), null, StandardCharsets.UTF_8,
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
    void keycloakDownIsRetriedThenSurfacesAsBadGateway() {
        doThrow(unavailable()).when(keycloakFeign).logout("kc-1");

        assertThatThrownBy(() -> keycloak.logout("kc-1")).satisfies(ResilienceWiringTest::assertBadGateway);
        verify(keycloakFeign, times(3)).logout("kc-1");
    }

    @Test
    void unknownKeycloakUserIsABusinessAnswerNotRetriedAndNotCounted() {
        doThrow(new FeignException.NotFound("nf", request(), null, null))
                .when(keycloakFeign).updateUser(anyString(), any());

        for (int i = 0; i < 12; i++) {
            assertThatThrownBy(() -> keycloak.setEnabled("kc-x", false))
                    .isInstanceOfSatisfying(AppException.class,
                            a -> assertThat(a.getErrorCode()).isEqualTo(IdentityErrorCode.USER_NOT_FOUND));
        }
        verify(keycloakFeign, times(12)).updateUser(anyString(), any());
        assertThat(breakers.circuitBreaker("keycloakAdmin").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void whenCatalogKeepsFailingTheCircuitOpensAndCallsStopReachingIt() {
        when(mediaFeign.getById("m", "u")).thenThrow(unavailable());

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> media.getById("m", "u")).satisfies(ResilienceWiringTest::assertBadGateway);
        }
        assertThat(breakers.circuitBreaker("catalogService").getState()).isEqualTo(CircuitBreaker.State.OPEN);

        reset(mediaFeign);
        assertThatThrownBy(() -> media.getById("m", "u")).satisfies(ResilienceWiringTest::assertBadGateway);
        verify(mediaFeign, never()).getById(anyString(), anyString());
    }
}
