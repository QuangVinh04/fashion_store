package com.fashionstore.keycloak.events;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publish event lên exchange dùng chung của hệ thống, chạy ở một thread riêng để RabbitMQ chậm hay chết
 * không làm chậm hoặc làm lỗi đăng nhập / đăng ký của Keycloak.
 * <p>
 * Đánh đổi: at-most-once. Keycloak không có outbox, nên event mất nếu RabbitMQ chết lúc đó hoặc Keycloak sập
 * trước khi gửi xong. Consumer (identity) vẫn tạo user ở request đầu tiên (JIT) làm đường dự phòng.
 */
final class RabbitEventPublisher implements AutoCloseable {

    // Phải khớp RabbitTopology (common-library): RabbitMQ từ chối khai báo lại exchange với tham số khác.
    // RabbitEventPublisherRabbitIntegrationTest đối chiếu các hằng số này với RabbitTopology.
    static final String EXCHANGE = "fashion.events";
    static final String UNROUTED_EXCHANGE = "fashion.events.unrouted";
    static final Map<String, Object> EXCHANGE_ARGUMENTS = Map.of("alternate-exchange", UNROUTED_EXCHANGE);

    private static final Logger LOG = Logger.getLogger(RabbitEventPublisher.class);
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final long CONFIRM_TIMEOUT_MS = 5000;
    private static final int PENDING_LIMIT = 1000;
    private static final int PERSISTENT = 2;

    private final ConnectionFactory factory = new ConnectionFactory();
    private final ThreadPoolExecutor executor;
    private Connection connection;

    RabbitEventPublisher(RabbitSettings settings) {
        factory.setHost(settings.host());
        factory.setPort(settings.port());
        factory.setUsername(settings.username());
        factory.setPassword(settings.password());
        factory.setVirtualHost(settings.virtualHost());
        factory.setConnectionTimeout(CONNECT_TIMEOUT_MS);
        factory.setHandshakeTimeout(CONNECT_TIMEOUT_MS);
        // Tự kết nối lại ở lần publish kế tiếp thay vì để thư viện chạy nền
        factory.setAutomaticRecoveryEnabled(false);
        executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(PENDING_LIMIT), runnable -> {
            Thread thread = new Thread(runnable, "fashion-rabbit-event-publisher");
            thread.setDaemon(true);
            return thread;
        }, (runnable, pool) -> LOG.warnf("Dropping a Keycloak event: %d events are already waiting for RabbitMQ", PENDING_LIMIT));
    }

    /** Trả về ngay; lỗi gửi chỉ được ghi log. */
    void publishAsync(OutgoingEvent event) {
        try {
            executor.execute(() -> publish(event));
        } catch (RejectedExecutionException e) {
            LOG.warnf("Dropping event %s: publisher is closed", event.messageId());
        }
    }

    private void publish(OutgoingEvent event) {
        Channel channel = null;
        try {
            channel = connection().createChannel();
            channel.confirmSelect();
            // Keycloak có thể chạy trước mọi service nên không chắc exchange đã có
            channel.exchangeDeclare(EXCHANGE, "direct", true, false, EXCHANGE_ARGUMENTS);
            AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                    .contentType("application/json")
                    .deliveryMode(PERSISTENT)
                    .messageId(event.messageId())
                    .build();
            channel.basicPublish(EXCHANGE, event.routingKey(), properties, event.body());
            channel.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
            LOG.debugf("Published %s (%s)", event.routingKey(), event.messageId());
        } catch (IOException | TimeoutException e) {
            LOG.warnf("Cannot publish event %s (%s) to RabbitMQ: %s", event.routingKey(), event.messageId(), e.getMessage());
            dropConnection();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warnf("Interrupted while publishing event %s", event.messageId());
        } finally {
            closeQuietly(channel);
        }
    }

    private synchronized Connection connection() throws IOException, TimeoutException {
        if (connection == null || !connection.isOpen()) {
            connection = factory.newConnection("keycloak-event-listener");
        }
        return connection;
    }

    private synchronized void dropConnection() {
        closeQuietly(connection);
        connection = null;
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        dropConnection();
    }

    // Đóng channel / connection đã hỏng: không còn gì để làm nếu việc đóng cũng lỗi, nên chỉ ghi log mức debug
    private static void closeQuietly(AutoCloseable resource) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (Exception e) {
            LOG.debugf("Ignoring failure while closing %s: %s", resource.getClass().getSimpleName(), e.getMessage());
        }
    }
}
