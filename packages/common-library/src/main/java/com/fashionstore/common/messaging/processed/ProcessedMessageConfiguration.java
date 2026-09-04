package com.fashionstore.common.messaging.processed;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcOperations;

@Configuration(proxyBeanMethods = false)
class ProcessedMessageConfiguration {

    /**
     * Redis là tùy chọn: service nào không cấu hình Redis vẫn chạy đúng, chỉ mất bộ lọc nhanh.
     */
    @Bean
    ProcessedMessageService processedMessageService(
            JdbcOperations jdbcOperations,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider
    ) {
        return new ProcessedMessageService(redisTemplateProvider.getIfAvailable(), jdbcOperations);
    }
}
