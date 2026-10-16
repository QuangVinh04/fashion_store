package com.fashionstore.common.config.feign;

import static org.assertj.core.api.Assertions.assertThat;

import feign.Request;
import feign.Retryer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

class FeignGlobalConfigTest {

    final FeignGlobalConfig config = new FeignGlobalConfig();

    @Test
    void everyInternalClientGetsBoundedTimeoutsInsteadOfTheLibraryDefaults() {
        Request.Options options = config.feignRequestOptions();

        assertThat(options.connectTimeoutMillis()).isEqualTo(TimeUnit.SECONDS.toMillis(2));
        assertThat(options.readTimeoutMillis()).isEqualTo(TimeUnit.SECONDS.toMillis(5));
        assertThat(options.isFollowRedirects()).isTrue();
    }

    @Test
    void feignStillNeverRetriesOnItsOwn() {
        assertThat(config.retryer()).isSameAs(Retryer.NEVER_RETRY);
    }

    @Test
    void theCorrelationInterceptorIsPartOfTheSharedConfiguration() {
        assertThat(config.correlationIdFeignInterceptor()).isInstanceOf(CorrelationIdFeignInterceptor.class);
    }
}
