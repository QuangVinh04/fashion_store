package com.fashionstore.common.config.feign;

import static org.assertj.core.api.Assertions.assertThat;

import com.fashionstore.common.web.CorrelationIdFilter;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class CorrelationIdFeignInterceptorTest {

    final CorrelationIdFeignInterceptor interceptor = new CorrelationIdFeignInterceptor();

    @AfterEach
    void clear() {
        MDC.remove(CorrelationIdFilter.MDC_KEY);
    }

    @Test
    void forwardsTheCurrentCorrelationIdSoLogsJoinAcrossServices() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-123");
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers().get(CorrelationIdFilter.HEADER)).containsExactly("corr-123");
    }

    @Test
    void addsNothingWhenThereIsNoCorrelationIdOnThisThread() {
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey(CorrelationIdFilter.HEADER);
    }

    @Test
    void neverOverridesAHeaderTheCallerSetExplicitly() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "from-mdc");
        RequestTemplate template = new RequestTemplate().header(CorrelationIdFilter.HEADER, "explicit");

        interceptor.apply(template);

        assertThat(template.headers().get(CorrelationIdFilter.HEADER)).containsExactly("explicit");
    }
}
