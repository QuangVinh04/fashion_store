package com.fashionstore.common.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import org.junit.jupiter.api.Test;

class UpstreamFailureTest {

    private static final Request REQUEST =
            Request.create(Request.HttpMethod.GET, "http://x/y", Collections.emptyMap(), null, null, null);

    private final UpstreamFailure predicate = new UpstreamFailure();

    private static FeignException status(int status) {
        return FeignException.errorStatus("m", feign.Response.builder().status(status).request(REQUEST)
                .headers(Collections.emptyMap()).build());
    }

    @Test
    void serverErrorsAndNetworkFailuresCountAsUpstreamFailures() {
        assertThat(predicate.test(status(500))).isTrue();
        assertThat(predicate.test(status(503))).isTrue();
        assertThat(predicate.test(new RetryableException(-1, "refused", Request.HttpMethod.GET,
                new IOException("refused"), (Long) null, REQUEST))).isTrue();
        assertThat(predicate.test(new IOException("reset"))).isTrue();
        assertThat(predicate.test(new IllegalStateException("bad body"))).isTrue();
    }

    @Test
    void businessAnswersAreNotUpstreamFailures() {
        assertThat(predicate.test(status(400))).isFalse();
        assertThat(predicate.test(status(404))).isFalse();
        assertThat(predicate.test(status(409))).isFalse();
        assertThat(predicate.test(new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR))).isFalse();
    }

    @Test
    void anOpenCircuitIsNeverRetriedOrRecordedAgain() {
        CircuitBreaker open = CircuitBreaker.ofDefaults("t");
        open.transitionToOpenState();
        assertThat(predicate.test(CallNotPermittedException.createCallNotPermittedException(open))).isFalse();
    }

    @Test
    void translateTurnsInfrastructureFailuresIntoBadGatewayAndKeepsBusinessAnswers() {
        assertThat(UpstreamFailure.translate(status(503)))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UPSTREAM_SERVICE_ERROR));
        CircuitBreaker open = CircuitBreaker.ofDefaults("t");
        open.transitionToOpenState();
        assertThat(UpstreamFailure.translate(CallNotPermittedException.createCallNotPermittedException(open)))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UPSTREAM_SERVICE_ERROR));

        FeignException notFound = status(404);
        assertThat(UpstreamFailure.translate(notFound)).isSameAs(notFound);
        AppException business = new AppException(ErrorCode.VALIDATION_FAILED);
        assertThat(UpstreamFailure.translate(business)).isSameAs(business);
    }
}
