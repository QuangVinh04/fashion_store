package com.fashionstore.common.autoconfigure;

import com.fashionstore.common.exception.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonErrorHandlingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    GlobalExceptionHandler commonExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
