package com.fashionstore.common.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fashionstore.common.redis.RedisService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

class CommonRedisAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(CommonRedisAutoConfiguration.class))
                    .withPropertyValues("spring.application.name=order-service");

    @Test
    void exposesOneRedisServiceNamespacedByTheApplicationName() {
        runner.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(RedisService.class);
                    assertThat(context.getBean(RedisService.class).key("a"))
                            .isEqualTo("fashion-store:order-service:a");
                });
    }

    @Test
    void stillProvidesAnInertRedisServiceWhenThereIsNoRedisConnection() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(RedisService.class);
            assertThat(context.getBean(RedisService.class).get("k")).isEmpty();
        });
    }

    @Test
    void aServiceMayReplaceItWithItsOwn() {
        RedisService custom = RedisService.disabled();
        runner.withBean(RedisService.class, () -> custom)
                .run(context -> assertThat(context.getBean(RedisService.class)).isSameAs(custom));
    }
}
