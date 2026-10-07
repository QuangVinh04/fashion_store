package com.fashionstore.common.messaging.outbox;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Gửi một message và <b>chỉ trả về bình thường khi broker xác nhận đã nhận và đã route được</b>.
 *
 * <p>Vì sao cần: {@code rabbitTemplate.send()} trả về ngay khi bytes được ghi ra socket. Broker có thể
 * sau đó từ chối (nack), mất kết nối giữa chừng, hoặc không tìm được queue nào cho routing key — cả ba
 * trường hợp đều không ném exception. Nếu relay outbox đánh dấu {@code PUBLISHED} ngay sau {@code send()},
 * message có thể mất mà không ai biết.
 *
 * <p>Cách làm (publisher confirms + returns của RabbitMQ):
 * <ol>
 *   <li>Gắn {@link CorrelationData} vào message để broker trả lời riêng cho đúng message này.</li>
 *   <li>Broker gửi {@code basic.ack} khi đã nhận (đã ghi đĩa với queue durable), hoặc {@code basic.nack}.</li>
 *   <li>Với cờ {@code mandatory}, message không route được sẽ bị trả lại ({@code basic.return}) trước ack.</li>
 *   <li>Chờ future của CorrelationData tối đa {@code confirmTimeout}; quá hạn coi như thất bại.</li>
 * </ol>
 * Bất kỳ thất bại nào cũng ném {@link AmqpException} để relay giữ dòng outbox ở trạng thái chờ gửi lại.
 *
 * <p>Gửi lại có thể tạo bản trùng (ví dụ broker đã nhận nhưng ack bị mất) — chấp nhận được vì mọi consumer
 * đều idempotent qua {@code ProcessedMessageService}.
 */
public class ConfirmedRabbitSender {

    private final RabbitTemplate rabbitTemplate;
    private final Duration confirmTimeout;

    public ConfirmedRabbitSender(RabbitTemplate rabbitTemplate, Duration confirmTimeout) {
        // Fail fast lúc khởi động: thiếu cấu hình thì future không bao giờ hoàn tất / return bị bỏ qua.
        if (!rabbitTemplate.getConnectionFactory().isPublisherConfirms()) {
            throw new IllegalStateException(
                    "ConfirmedRabbitSender cần spring.rabbitmq.publisher-confirm-type=correlated");
        }
        if (!Boolean.TRUE.equals(rabbitTemplate.isMandatoryFor(new Message(new byte[0])))) {
            throw new IllegalStateException(
                    "ConfirmedRabbitSender cần spring.rabbitmq.template.mandatory=true để phát hiện message không route được");
        }
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeout = confirmTimeout;
    }

    /**
     * @param correlationId id để đối chiếu confirm, thường là id dòng outbox (xuất hiện trong log lỗi)
     * @throws AmqpException khi broker nack, không route được, hoặc không trả lời kịp
     */
    public void send(String exchange, String routingKey, Message message, String correlationId) {
        CorrelationData correlation = new CorrelationData(correlationId);
        rabbitTemplate.send(exchange, routingKey, message, correlation);

        CorrelationData.Confirm confirm = awaitConfirm(correlation, correlationId);
        if (!confirm.isAck()) {
            throw new AmqpException("Broker từ chối message " + correlationId + ": " + confirm.getReason());
        }

        // RabbitMQ gửi basic.return TRƯỚC basic.ack, nên tới đây thông tin return (nếu có) đã được gắn vào.
        ReturnedMessage returned = correlation.getReturned();
        if (returned != null) {
            throw new AmqpException("Message " + correlationId + " không route được tới queue nào ("
                    + returned.getReplyText() + ", exchange=" + returned.getExchange()
                    + ", routingKey=" + returned.getRoutingKey() + ")");
        }
    }

    private CorrelationData.Confirm awaitConfirm(CorrelationData correlation, String correlationId) {
        try {
            return correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            throw new AmqpException("Broker không xác nhận message " + correlationId
                    + " trong " + confirmTimeout.toMillis() + "ms", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Bị ngắt khi chờ xác nhận message " + correlationId, exception);
        } catch (ExecutionException exception) {
            throw new AmqpException("Lỗi khi chờ xác nhận message " + correlationId, exception.getCause());
        }
    }
}
