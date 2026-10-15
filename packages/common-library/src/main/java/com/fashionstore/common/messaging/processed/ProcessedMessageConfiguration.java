package com.fashionstore.common.messaging.processed;

import com.fashionstore.common.redis.RedisService;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;

@Configuration(proxyBeanMethods = false)
class ProcessedMessageConfiguration {

    /**
     * Redis là tùy chọn: service nào không có Redis vẫn chạy đúng, chỉ mất bộ lọc nhanh.
     */
    @Bean
    ProcessedMessageService processedMessageService(
            JdbcOperations jdbcOperations, ObjectProvider<RedisService> redis) {
        return new ProcessedMessageService(redis.getIfAvailable(RedisService::disabled), jdbcOperations);
    }
}
