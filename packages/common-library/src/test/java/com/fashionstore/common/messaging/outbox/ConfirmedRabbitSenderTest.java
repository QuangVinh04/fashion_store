package com.fashionstore.common.messaging.outbox;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Chạy trên broker thật: confirm/return là hành vi của RabbitMQ, mock không chứng minh được. */
class ConfirmedRabbitSenderTest {

    static final GenericContainer<?> BROKER = new GenericContainer<>("rabbitmq:3.13-management")
            .withExposedPorts(5672)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1))
            .withStartupTimeout(Duration.ofMinutes(2));

    CachingConnectionFactory connectionFactory;
    RabbitTemplate template;
    RabbitAdmin admin;

    @BeforeAll
    static void startBroker() {
        BROKER.start();
    }

    @AfterAll
    static void stopBroker() {
        BROKER.stop();
    }

    @BeforeEach
    void setUp() {
        connectionFactory = connectionFactory(CachingConnectionFactory.ConfirmType.CORRELATED);
        template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        admin = new RabbitAdmin(connectionFactory);
        DirectExchange exchange = new DirectExchange("sender.test");
        Queue queue = new Queue("sender.test.queue");
        admin.declareExchange(exchange);
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with("routed"));
        admin.purgeQueue("sender.test.queue");
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    void returnsNormallyOnlyAfterBrokerAcknowledgedARoutedMessage() {
        ConfirmedRabbitSender sender = new ConfirmedRabbitSender(template, Duration.ofSeconds(5));

        sender.send("sender.test", "routed", message(), "event-1");

        Message received = template.receive("sender.test.queue", 2000);
        assertThat(received).isNotNull();
        assertThat(new String(received.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"ok\":true}");
    }

    @Test
    void unroutableMessageFailsInsteadOfBeingSilentlyDropped() {
        ConfirmedRabbitSender sender = new ConfirmedRabbitSender(template, Duration.ofSeconds(5));

        assertThatThrownBy(() -> sender.send("sender.test", "nobody.listens", message(), "event-2"))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("event-2")
                .hasMessageContaining("NO_ROUTE");
    }

    @Test
    void missingExchangeFailsBecauseBrokerNeverAcknowledges() {
        ConfirmedRabbitSender sender = new ConfirmedRabbitSender(template, Duration.ofSeconds(5));

        assertThatThrownBy(() -> sender.send("exchange.does.not.exist", "routed", message(), "event-3"))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("event-3");
    }

    @Test
    void refusesTemplateWithoutPublisherConfirms() {
        CachingConnectionFactory plain = connectionFactory(CachingConnectionFactory.ConfirmType.NONE);
        try {
            RabbitTemplate noConfirms = new RabbitTemplate(plain);
            noConfirms.setMandatory(true);

            assertThatThrownBy(() -> new ConfirmedRabbitSender(noConfirms, Duration.ofSeconds(5)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("publisher-confirm-type");
        } finally {
            plain.destroy();
        }
    }

    @Test
    void refusesTemplateWithoutMandatoryFlag() {
        template.setMandatory(false);

        assertThatThrownBy(() -> new ConfirmedRabbitSender(template, Duration.ofSeconds(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mandatory");
    }

    private static CachingConnectionFactory connectionFactory(CachingConnectionFactory.ConfirmType confirmType) {
        CachingConnectionFactory factory = new CachingConnectionFactory(BROKER.getHost(), BROKER.getMappedPort(5672));
        factory.setPublisherConfirmType(confirmType);
        factory.setPublisherReturns(confirmType == CachingConnectionFactory.ConfirmType.CORRELATED);
        return factory;
    }

    private static Message message() {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        return new Message("{\"ok\":true}".getBytes(StandardCharsets.UTF_8), properties);
    }
}
