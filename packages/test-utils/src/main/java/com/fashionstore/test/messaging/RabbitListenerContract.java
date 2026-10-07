package com.fashionstore.test.messaging;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.listener.adapter.HandlerAdapter;
import org.springframework.amqp.rabbit.listener.adapter.MessagingMessageListenerAdapter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/** Actual-broker contract shared by consumer services; never connects to their running broker. */
public abstract class RabbitListenerContract {
    private static final GenericContainer<?> BROKER = new GenericContainer<>("rabbitmq:3.13-management")
            .withEnv("RABBITMQ_DEFAULT_USER", "phase1")
            .withEnv("RABBITMQ_DEFAULT_PASS", "test-only")
            .withExposedPorts(5672)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1))
            .withStartupTimeout(Duration.ofMinutes(2));

    protected abstract Class<?> rabbitConfiguration();
    protected abstract List<String> sourceQueues();
    protected abstract String deadLetterQueue();

    /**
     * Broker sạch, không nạp policy nào: mọi thuộc tính queue (quorum, DLX, delivery-limit) phải đến
     * từ chính {@code RabbitMQConfig} của service — đúng như khi service chạy trên một broker mới.
     */
    @BeforeAll
    static void startBroker() {
        BROKER.start();
    }

    @Test
    void everyQueueOfTheServiceIsQuorum() {
        withContext(context -> {
            List<String> queues = new ArrayList<>(sourceQueues());
            queues.add(deadLetterQueue());
            try {
                var result = BROKER.execInContainer("rabbitmqctl", "list_queues", "-q", "--no-table-headers", "name", "type");
                for (String queue : queues) {
                    assertThat(result.getStdout()).as("queue type of " + queue)
                            .containsPattern("(?m)^" + java.util.regex.Pattern.quote(queue) + "\\s+quorum$");
                }
            } catch (Exception exception) { throw new AssertionError(exception); }
        });
    }

    @Test
    void eventWithoutAnyConsumerIsKeptInUnroutedQueueInsteadOfDropped() {
        withContext(context -> {
            RabbitTemplate template = context.getBean(RabbitTemplate.class);
            template.send("fashion.events", "event.nobody.listens", new Message("{}".getBytes(StandardCharsets.UTF_8)));
            assertThat(template.receive("fashion.events.unrouted", 5000)).as("unrouted message").isNotNull();
        });
    }

    @AfterAll
    static void stopBroker() {
        BROKER.stop();
    }

    @Test
    void transientFailureRetriesWithYamlBackoffThenAcknowledges() {
        withContext(context -> {
            AtomicInteger calls = new AtomicInteger();
            List<Long> times = new CopyOnWriteArrayList<>();
            CountDownLatch success = new CountDownLatch(1);
            listen(context, sourceQueues().getFirst(), message -> {
                times.add(System.nanoTime());
                if (calls.incrementAndGet() < 3) { throw new IllegalStateException("temporary database outage"); }
                success.countDown();
            }, () -> {
                send(context, sourceQueues().getFirst());
                try { assertThat(success.await(5, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException exception) { throw new AssertionError(exception); }
            });
            assertThat(calls).hasValue(3);
            assertThat(TimeUnit.NANOSECONDS.toMillis(times.get(1) - times.get(0))).isGreaterThanOrEqualTo(60);
            assertThat(TimeUnit.NANOSECONDS.toMillis(times.get(2) - times.get(1))).isGreaterThanOrEqualTo(120);
            assertThat(context.getBean(RabbitTemplate.class).receive(deadLetterQueue(), 100)).isNull();
        });
    }

    @Test
    void exhaustedRetriesDeadLetterAfterThreeAttempts() {
        assertFailureAttempts(3, new IllegalStateException("temporary database outage"));
    }

    @Test
    void overriddenYamlAttemptLimitIsApplied() {
        withContext(context -> assertFailureAttempts(context, sourceQueues().getFirst(), 2,
                new IllegalStateException("temporary database outage")), "spring.rabbitmq.listener.simple.retry.max-attempts=2");
    }

    @Test
    void wrappedInvalidMessageIsDeadLetteredWithoutRetry() {
        assertFailureAttempts(1, new IllegalStateException("listener wrapper", new IllegalArgumentException("bad payload")));
    }

    @Test
    void unhandledBusinessRejectionIsNotRetried() {
        assertFailureAttempts(1, new AppException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void upstreamServerFailureStillGetsBoundedRetry() {
        assertFailureAttempts(3, new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR));
    }

    @Test
    void requiredHeaderResolutionFailureIsNotRetried() {
        assertAdapterAttempts(true, 1);
    }

    @Test
    void realListenerInvocationWrapperPreservesTransientRetries() {
        assertAdapterAttempts(false, 3);
    }

    @Test
    void springArgumentValidationFailureIsNotRetried() throws NoSuchMethodException {
        var parameter = new org.springframework.core.MethodParameter(
                RequiredHeaderProbe.class.getMethod("receive", String.class), 0);
        assertFailureAttempts(1, new org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException(
                new org.springframework.messaging.support.GenericMessage<>("invalid payload"), parameter));
    }

    private void assertAdapterAttempts(boolean omitHeader, int expectedAttempts) {
        withContext(context -> {
            RequiredHeaderProbe probe = new RequiredHeaderProbe();
            AtomicInteger attempts = new AtomicInteger();
            DefaultMessageHandlerMethodFactory methodFactory = new DefaultMessageHandlerMethodFactory();
            methodFactory.afterPropertiesSet();
            try {
                var method = RequiredHeaderProbe.class.getMethod("receive", String.class);
                MessagingMessageListenerAdapter adapter = new MessagingMessageListenerAdapter(probe, method) {
                    @Override
                    public void onMessage(Message message, com.rabbitmq.client.Channel channel) throws Exception {
                        attempts.incrementAndGet();
                        super.onMessage(message, channel);
                    }
                };
                adapter.setHandlerAdapter(new HandlerAdapter(methodFactory.createInvocableHandlerMethod(probe, method)));
                adapter.setMessageConverter(context.getBeanProvider(MessageConverter.class)
                        .getIfAvailable(org.springframework.amqp.support.converter.SimpleMessageConverter::new));
                Message[] dead = new Message[1];
                listen(context, sourceQueues().getFirst(), adapter, () -> {
                    MessageProperties properties = new MessageProperties();
                    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                    properties.setMessageId("adapter-message");
                    if (!omitHeader) { properties.setHeader("outboxEventId", "adapter-message"); }
                    context.getBean(RabbitTemplate.class).send("", sourceQueues().getFirst(),
                            new Message("{}".getBytes(StandardCharsets.UTF_8), properties));
                    dead[0] = context.getBean(RabbitTemplate.class).receive(deadLetterQueue(), 5000);
                });
                assertThat(dead[0]).isNotNull();
                assertThat(attempts).hasValue(expectedAttempts);
                assertThat(probe.calls).hasValue(omitHeader ? 0 : 3);
            } catch (NoSuchMethodException exception) { throw new AssertionError(exception); }
        });
    }

    public static class RequiredHeaderProbe {
        final AtomicInteger calls = new AtomicInteger();

        @RabbitListener(queues = "unused-by-test")
        public void receive(@Header("outboxEventId") String messageId) {
            calls.incrementAndGet();
            throw new IllegalStateException("temporary database outage");
        }
    }

    @Test
    void everyExistingConsumerQueueRoutesFailuresToItsServiceDlq() {
        withContext(context -> {
            for (String queue : sourceQueues()) {
                assertFailureAttempts(context, queue, 1, new IllegalArgumentException("unsupported event type"));
            }
        });
    }

    private void assertFailureAttempts(int expected, RuntimeException failure) {
        withContext(context -> assertFailureAttempts(context, sourceQueues().getFirst(), expected, failure));
    }

    private void assertFailureAttempts(AssertableApplicationContext context, String queue, int expected, RuntimeException failure) {
        RabbitAdmin admin = context.getBean(RabbitAdmin.class);
        assertThat(admin.getQueueProperties(deadLetterQueue())).as("declared DLQ " + deadLetterQueue()).isNotNull();
        admin.purgeQueue(deadLetterQueue());
        AtomicInteger calls = new AtomicInteger();
        Message[] dead = new Message[1];
        listen(context, queue, message -> {
            // Bound the broken pre-fix requeue loop in this test, never in production code.
            if (calls.incrementAndGet() >= 5) { throw new AmqpRejectAndDontRequeueException("test loop guard"); }
            throw failure;
        }, () -> {
            send(context, queue);
            dead[0] = context.getBean(RabbitTemplate.class).receive(deadLetterQueue(), 5000);
        });
        assertThat(dead[0]).as("dead-lettered message from " + queue).isNotNull();
        assertThat(calls).hasValue(expected);
        assertThat((Object) dead[0].getMessageProperties().getHeader("outboxEventId")).isEqualTo("phase1-message");
        List<?> deaths = dead[0].getMessageProperties().getHeader("x-death");
        assertThat(deaths).isNotEmpty();
        assertThat(deaths.getFirst().toString()).contains(queue).contains("rejected");
        assertThat(context.getBean(RabbitTemplate.class).receive(queue, 100)).isNull();
    }

    private void listen(AssertableApplicationContext context, String queue, MessageListener listener, Runnable publishAndAssert) {
        SimpleMessageListenerContainer container = context.getBean(SimpleRabbitListenerContainerFactory.class).createListenerContainer();
        container.setQueueNames(queue);
        container.setMessageListener(listener);
        container.setShutdownTimeout(1000);
        container.start();
        try { publishAndAssert.run(); }
        finally { container.stop(); }
    }

    private static void send(AssertableApplicationContext context, String queue) {
        MessageProperties properties = new MessageProperties();
        properties.setHeader("outboxEventId", "phase1-message");
        properties.setMessageId("phase1-message");
        context.getBean(RabbitTemplate.class).send("", queue,
                new Message("{}".getBytes(StandardCharsets.UTF_8), properties));
    }

    private void withContext(Consumer<AssertableApplicationContext> assertion, String... overrides) {
        List<Class<?>> autoConfigurations = new ArrayList<>(List.of(RabbitAutoConfiguration.class));
        // Exercise common auto-configuration as discovered from its real imports metadata.
        for (String candidate : ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())) {
            if (candidate.equals("com.fashionstore.common.autoconfigure.CommonRabbitAutoConfiguration")) {
                try { autoConfigurations.add(Class.forName(candidate)); }
                catch (ClassNotFoundException exception) { throw new AssertionError(exception); }
            }
        }
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(autoConfigurations.toArray(Class<?>[]::new)))
                .withUserConfiguration(rabbitConfiguration())
                .withInitializer(context -> {
                    try {
                        new YamlPropertySourceLoader().load("service-yaml", new ClassPathResource("application.yaml"))
                                .forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                    } catch (Exception exception) { throw new AssertionError(exception); }
                })
                .withPropertyValues("spring.rabbitmq.host=" + BROKER.getHost(),
                        "spring.rabbitmq.port=" + BROKER.getMappedPort(5672),
                        "spring.rabbitmq.username=phase1", "spring.rabbitmq.password=test-only",
                        "spring.rabbitmq.listener.simple.retry.initial-interval=80ms")
                .withPropertyValues(overrides)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    context.getBean(RabbitAdmin.class).initialize();
                    for (String queue : sourceQueues()) { context.getBean(RabbitAdmin.class).purgeQueue(queue); }
                    assertion.accept(context);
                });
    }
}
