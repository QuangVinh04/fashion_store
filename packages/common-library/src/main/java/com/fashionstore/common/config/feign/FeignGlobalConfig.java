package com.fashionstore.common.config.feign;

import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class FeignGlobalConfig {

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public Retryer retryer() {
        return Retryer.NEVER_RETRY;
    }

    /**
     * Timeout chặt cho mọi lời gọi nội bộ: không có thì mặc định của thư viện (đọc tới cả phút) khiến một
     * service láng giềng treo kéo theo cạn luồng của service gọi. Chỉnh theo từng client qua
     * {@code spring.cloud.openfeign.client.config.<tên>.connect-timeout / read-timeout}.
     */
    @Bean
    public Request.Options feignRequestOptions() {
        return new Request.Options(2, TimeUnit.SECONDS, 5, TimeUnit.SECONDS, true);
    }

    @Bean
    public RequestInterceptor correlationIdFeignInterceptor() {
        return new CorrelationIdFeignInterceptor();
    }
}
