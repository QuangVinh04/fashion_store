package com.fashionstore.common.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;

/**
 * Một nơi duy nhất định nghĩa "hình dạng" RabbitMQ của cả hệ thống.
 *
 * <pre>
 *  producer ──► fashion.events (direct) ──routing key = EventTypes.X──► &lt;service&gt;.&lt;event&gt; (quorum)
 *                    │                                                        │ lỗi vĩnh viễn / hết retry
 *                    │ không queue nào bind routing key này                   ▼
 *                    ▼                                              fashion.events.dlx ──► &lt;service&gt;.dlq
 *            fashion.events.unrouted (alternate exchange) ──► fashion.events.unrouted (queue)
 * </pre>
 *
 * <p>Vì sao gom vào common-library: RabbitMQ từ chối khai báo lại một exchange/queue với tham số khác
 * ({@code PRECONDITION_FAILED}). Nếu mỗi service tự viết tham số cho {@code fashion.events}, chỉ cần một
 * service lệch là service đó không khởi động được. Dùng chung factory này thì mọi service khai báo giống hệt.
 *
 * <p>Chỉ là hạ tầng (tên exchange, kiểu queue); routing key nghiệp vụ vẫn nằm ở {@code EventTypes}.
 */
public final class RabbitTopology {

    /** Exchange chính, mọi event/command của hệ thống đi qua đây. */
    public static final String EXCHANGE = "fashion.events";

    /** Message bị consumer từ chối (lỗi vĩnh viễn hoặc hết lượt retry) được broker chuyển tới đây. */
    public static final String DEAD_LETTER_EXCHANGE = "fashion.events.dlx";

    /**
     * Alternate exchange: message có routing key mà chưa queue nào bind (ví dụ ORDER_CONFIRMED khi chưa có
     * consumer) được giữ lại ở queue cùng tên thay vì bị broker âm thầm xoá.
     */
    public static final String UNROUTED_EXCHANGE = "fashion.events.unrouted";
    public static final String UNROUTED_QUEUE = "fashion.events.unrouted";

    /** Header mang id của dòng outbox — consumer dùng làm khoá idempotency. */
    public static final String OUTBOX_EVENT_ID_HEADER = "outboxEventId";

    /**
     * Số lần broker giao lại cùng một message (do consumer chết / mất kết nối giữa chừng) trước khi đẩy
     * sang DLQ. Retry nghiệp vụ do Spring Retry lo trong bộ nhớ; con số này chỉ là lưới an toàn chống
     * một message độc làm consumer crash lặp vô hạn.
     */
    public static final int DELIVERY_LIMIT = 5;

    private RabbitTopology() {
    }

    public static DirectExchange eventsExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE)
                .durable(true)
                .alternate(UNROUTED_EXCHANGE)
                .build();
    }

    public static DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    /** Exchange + queue hứng message không có người nhận. Service nào khai báo {@link #eventsExchange()} thì khai báo kèm cái này. */
    public static Declarables unroutedTopology() {
        FanoutExchange exchange = ExchangeBuilder.fanoutExchange(UNROUTED_EXCHANGE).durable(true).build();
        Queue queue = QueueBuilder.durable(UNROUTED_QUEUE).quorum().build();
        return new Declarables(exchange, queue, BindingBuilder.bind(queue).to(exchange));
    }

    /**
     * Queue của consumer: quorum (nhân bản trên cluster, có delivery-limit) và gắn DLX trỏ về DLQ của service.
     *
     * @param name            tên queue, quy ước {@code <service>.<event>}
     * @param deadLetterQueue DLQ của service sở hữu queue, ví dụ {@code payment.dlq}
     */
    public static Queue consumerQueue(String name, String deadLetterQueue) {
        return QueueBuilder.durable(name)
                .quorum()
                .deliveryLimit(DELIVERY_LIMIT)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(deadLetterQueue)
                .build();
    }

    /** DLQ của một service. Không gắn DLX nữa — đây là điểm cuối, chờ người xem và replay. */
    public static Queue deadLetterQueue(String name) {
        return QueueBuilder.durable(name).quorum().build();
    }

    /** DLQ nhận message từ DLX với routing key chính là tên DLQ (khớp {@link #consumerQueue}). */
    public static Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(deadLetterQueue.getName());
    }

    public static Binding bind(Queue queue, DirectExchange eventsExchange, String eventType) {
        return BindingBuilder.bind(queue).to(eventsExchange).with(eventType);
    }
}
