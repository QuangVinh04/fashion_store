package com.fashionstore.order.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;
import java.time.Clock;

@Configuration
public class WorkflowClockConfiguration {
    @Bean @ConditionalOnMissingBean(Clock.class)
    public Clock workflowClock() { return Clock.systemUTC(); }
}
