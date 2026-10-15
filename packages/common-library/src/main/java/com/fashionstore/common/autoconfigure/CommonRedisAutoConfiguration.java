package com.fashionstore.common.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.common.redis.RedisService;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Cung cấp {@link RedisService}, cách duy nhất để service dùng Redis. Luôn có bean này: không có kết nối
 * Redis thì nó là no-op (xem {@link RedisService}). Gateway và BFF loại autoconfig này ra.
 */
@AutoConfiguration(after = RedisAutoConfiguration.class)
@ConditionalOnClass(StringRedisTemplate.class)
public class CommonRedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RedisService redisService(
            ObjectProvider<StringRedisTemplate> template,
            ObjectProvider<ObjectMapper> mapper,
            Environment environment) {
        return new RedisService(
                template.getIfAvailable(),
                mapper.getIfAvailable(() -> new ObjectMapper().findAndRegisterModules()),
                environment.getProperty("spring.application.name", "application"));
    }
}
