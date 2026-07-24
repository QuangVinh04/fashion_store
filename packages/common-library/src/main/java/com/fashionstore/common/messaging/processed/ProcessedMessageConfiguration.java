package com.fashionstore.common.messaging.processed;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;

@Configuration(proxyBeanMethods = false)
class ProcessedMessageConfiguration {

    @Bean
    ProcessedMessageService processedMessageService(JdbcOperations jdbcOperations) {
        return new ProcessedMessageService(jdbcOperations);
    }
}
