package com.fashionstore.keycloak.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.fashionstore.common.messaging.RabbitTopology;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Publisher trên một RabbitMQ thật, với đúng topology mà các service khai báo (RabbitTopology). */
@Tag("external-infrastructure")
class RabbitEventPublisherRabbitIntegrationTest {

    static final GenericContainer<?> RABBIT = new GenericContainer<>("rabbitmq:3.13-management")
            .withExposedPorts(5672)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1).withStartupTimeout(Duration.ofMinutes(2)));

    static {
        RABBIT.start();
    }

    @AfterAll
    static void stop() {
        RABBIT.stop();
    }

    @Test
    void theExchangeDeclarationMatchesWhatEveryServiceDeclaresSoRabbitDoesNotRejectEither() {
        assertThat(RabbitEventPublisher.EXCHANGE).isEqualTo(RabbitTopology.EXCHANGE);
        assertThat(RabbitEventPublisher.UNROUTED_EXCHANGE).isEqualTo(RabbitTopology.UNROUTED_EXCHANGE);
        assertThat(RabbitEventPublisher.EXCHANGE_ARGUMENTS).isEqualTo(RabbitTopology.eventsExchange().getArguments());
    }

    @Test
    void aPublishedEventReachesABoundQueueAsAPersistentJsonMessage() throws Exception {
        String routingKey = "it." + UUID.randomUUID();
        try (RabbitEventPublisher publisher = publisher(RABBIT.getMappedPort(5672));
             Connection connection = connect(); Channel channel = connection.createChannel()) {
            declareSharedExchange(channel);
            String queue = channel.queueDeclare().getQueue();
            channel.queueBind(queue, RabbitTopology.EXCHANGE, routingKey);

            publisher.publishAsync(new OutgoingEvent(routingKey, "msg-1", "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8)));

            GetResponse response = awaitMessage(channel, queue);
            assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"hello\":\"world\"}");
            AMQP.BasicProperties properties = response.getProps();
            assertThat(properties.getDeliveryMode()).isEqualTo(2);
            assertThat(properties.getContentType()).isEqualTo("application/json");
            assertThat(properties.getMessageId()).isEqualTo("msg-1");
        }
    }

    @Test
    void anEventNobodyIsBoundToLandsInTheUnroutedQueueInsteadOfBeingLost() throws Exception {
        try (RabbitEventPublisher publisher = publisher(RABBIT.getMappedPort(5672));
             Connection connection = connect(); Channel channel = connection.createChannel()) {
            declareSharedExchange(channel);
            channel.exchangeDeclare(RabbitTopology.UNROUTED_EXCHANGE, "fanout", true);
            String unrouted = channel.queueDeclare().getQueue();
            channel.queueBind(unrouted, RabbitTopology.UNROUTED_EXCHANGE, "");

            publisher.publishAsync(new OutgoingEvent("it.nobody-listens." + UUID.randomUUID(), "msg-2", "{}".getBytes(StandardCharsets.UTF_8)));

            assertThat(awaitMessage(channel, unrouted).getProps().getMessageId()).isEqualTo("msg-2");
        }
    }

    @Test
    void publishingWorksWhenTheSharedExchangeDoesNotExistYet() throws Exception {
        // Keycloak có thể khởi động trước mọi service: publisher phải tự khai báo exchange (đúng tham số)
        String routingKey = "it." + UUID.randomUUID();
        try (Connection connection = connect(); Channel channel = connection.createChannel()) {
            channel.exchangeDelete(RabbitTopology.EXCHANGE);
            try (RabbitEventPublisher publisher = publisher(RABBIT.getMappedPort(5672))) {
                publisher.publishAsync(new OutgoingEvent(routingKey, "msg-3", "{}".getBytes(StandardCharsets.UTF_8)));
                Thread.sleep(1500);
            }
            // Passive ném lỗi nếu exchange không tồn tại: chứng minh chính publisher đã tạo nó (không phải dòng bên dưới)
            channel.exchangeDeclarePassive(RabbitTopology.EXCHANGE);
            // Khai báo lại như một service: nếu publisher khai báo lệch tham số, dòng này ném PRECONDITION_FAILED
            declareSharedExchange(channel);
        }
    }

    @Test
    void aBrokerThatIsDownNeverBlocksOrBreaksTheCaller() {
        // Cổng 1: kết nối bị từ chối ngay. Đăng nhập / đăng ký của Keycloak không được chậm hay lỗi theo.
        try (RabbitEventPublisher publisher = publisher(1)) {
            long start = System.nanoTime();

            publisher.publishAsync(new OutgoingEvent("it.down", "msg-4", "{}".getBytes(StandardCharsets.UTF_8)));
            publisher.publishAsync(new OutgoingEvent("it.down", "msg-5", "{}".getBytes(StandardCharsets.UTF_8)));

            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isLessThan(500);
        }
    }

    private static RabbitEventPublisher publisher(int port) {
        return new RabbitEventPublisher(new RabbitSettings(RABBIT.getHost(), port, "guest", "guest", "/"));
    }

    private static Connection connect() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RABBIT.getHost());
        factory.setPort(RABBIT.getMappedPort(5672));
        return factory.newConnection();
    }

    // Y hệt RabbitTopology.eventsExchange() (durable, direct, alternate-exchange)
    private static void declareSharedExchange(Channel channel) throws IOException {
        channel.exchangeDeclare(RabbitTopology.EXCHANGE, "direct", true, false,
                Map.<String, Object>copyOf(RabbitTopology.eventsExchange().getArguments()));
    }

    private static GetResponse awaitMessage(Channel channel, String queue) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            GetResponse response = channel.basicGet(queue, true);
            if (response != null) {
                return response;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("No message arrived on " + queue + " within 10s");
    }
}
