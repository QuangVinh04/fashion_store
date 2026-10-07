package com.fashionstore.common.autoconfigure;

import com.fashionstore.common.messaging.outbox.ConfirmedRabbitSender;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CommonRabbitAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class, CommonRabbitAutoConfiguration.class));

    @Test
    void publisherServiceWithConfirmsGetsConfirmedSender() {
        runner.withPropertyValues(
                        "spring.rabbitmq.publisher-confirm-type=correlated",
                        "spring.rabbitmq.publisher-returns=true",
                        "spring.rabbitmq.template.mandatory=true")
                .run(context -> assertThat(context).hasSingleBean(ConfirmedRabbitSender.class));
    }

    @Test
    void consumerOnlyServiceWithoutConfirmsGetsNoSender() {
        runner.run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(ConfirmedRabbitSender.class));
    }

    @Test
    void confirmsWithoutMandatoryFailsAtStartupInsteadOfLosingUnroutableMessages() {
        runner.withPropertyValues("spring.rabbitmq.publisher-confirm-type=correlated")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("mandatory"));
    }
}
