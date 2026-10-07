package com.fashionstore.common.autoconfigure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.messaging.outbox.ConfirmedRabbitSender;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.amqp.RabbitRetryTemplateCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.policy.ExceptionClassifierRetryPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.time.Duration;

/** Shared listener retry policy, plus the confirmed sender for services that publish via an outbox. */
@AutoConfiguration(after = RabbitAutoConfiguration.class)
@ConditionalOnClass({RabbitTemplate.class, RetryTemplate.class})
@ConditionalOnBean(RabbitProperties.class)
public class CommonRabbitAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(name = "listenerRetryTemplateCustomizer")
    RabbitRetryTemplateCustomizer listenerRetryTemplateCustomizer(RabbitProperties properties) {
        return (target, template) -> {
            if (target != RabbitRetryTemplateCustomizer.Target.LISTENER) { return; }
            int maxAttempts = properties.getListener().getSimple().getRetry().getMaxAttempts();
            SimpleRetryPolicy retry = new SimpleRetryPolicy(maxAttempts);
            NeverRetryPolicy terminal = new NeverRetryPolicy();
            ExceptionClassifierRetryPolicy policy = new ExceptionClassifierRetryPolicy();
            policy.setExceptionClassifier(exception -> retryable(exception) ? retry : terminal);
            template.setRetryPolicy(policy); // Boot still supplies YAML backoff and the rejecting recoverer.
        };
    }

    /**
     * Chỉ service phát message (có outbox) mới bật publisher confirms; service chỉ tiêu thụ như
     * notification-service không cần bean này. Bật confirms mà quên mandatory thì constructor ném lỗi
     * ngay lúc khởi động.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RabbitTemplate.class)
    @ConditionalOnProperty(prefix = "spring.rabbitmq", name = "publisher-confirm-type", havingValue = "correlated")
    ConfirmedRabbitSender confirmedRabbitSender(RabbitTemplate rabbitTemplate) {
        return new ConfirmedRabbitSender(rabbitTemplate, Duration.ofSeconds(5));
    }

    private static boolean retryable(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof IllegalArgumentException || cause instanceof JsonProcessingException
                    // Validation API is optional in consumers such as notification-service.
                    || cause.getClass().getName().equals("jakarta.validation.ConstraintViolationException")
                    || cause instanceof org.springframework.amqp.support.converter.MessageConversionException
                    || cause instanceof org.springframework.messaging.converter.MessageConversionException
                    || cause instanceof org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException
                    // Missing required headers have no nested business failure to retry.
                    || (cause instanceof org.springframework.messaging.MessageHandlingException && cause.getCause() == null)
                    || cause instanceof AmqpRejectAndDontRequeueException) {
                return false;
            }
            if (cause instanceof AppException application && application.getErrorCode().getStatusCode().is4xxClientError()) {
                return false;
            }
        }
        return true;
    }
}
