package com.fashionstore.notification.messaging;

import com.fashionstore.common.messaging.RabbitTopology;
import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.notification.EmailNotificationRequested;
import com.fashionstore.notification.config.RabbitMQConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * Nhận lệnh gửi email ({@code notification.email.requested}) từ mọi service và gửi qua SMTP.
 *
 * <p>Idempotency dùng chung {@link ProcessedMessageService} như mọi consumer khác: id của message được
 * ghi vào {@code processed_message} trong cùng transaction, nên cùng một message chỉ gửi email một lần.
 * Còn một khe hở nhỏ không tránh được: email đã gửi xong mà commit DB lỗi thì lần giao lại sẽ gửi thêm
 * một email — với email, gửi trùng (at-least-once) vẫn tốt hơn bỏ sót.
 */
@Component
@RequiredArgsConstructor
public class EmailNotificationListener {

    static final String CONSUMER_NAME = "notification-email-v1";

    private final ObjectMapper objectMapper;
    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final ProcessedMessageService processedMessageService;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.EMAIL_QUEUE)
    public void handle(EventEnvelope<?> envelope, @Header(RabbitTopology.OUTBOX_EVENT_ID_HEADER) String messageId) {
        if (!EventTypes.NOTIFICATION_EMAIL_REQUESTED.equals(envelope.eventType())) {
            // Lỗi vĩnh viễn: retry vô ích, CommonRabbitAutoConfiguration đẩy thẳng sang DLQ.
            throw new IllegalArgumentException("Unsupported eventType on email queue: " + envelope.eventType());
        }
        processedMessageService.processOnce(messageId, CONSUMER_NAME, () -> {
            // Converter dựng payload thành Map (EventEnvelope<?>), chuyển tiếp sang record đúng kiểu.
            send(objectMapper.convertValue(envelope.payload(), EmailNotificationRequested.class));
        });
    }

    private void send(EmailNotificationRequested request) {
        Context context = new Context();
        if (request.variables() != null) {
            request.variables().forEach(context::setVariable);
        }
        String token = request.variables() != null ? request.variables().getOrDefault("verifyCode", "") : "";
        context.setVariable("verifyLink", frontendUrl + "/verify-email?token=" + token);

        String html = templateEngine.process(request.template(), context);
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setTo(request.recipient());
            helper.setSubject(resolveSubject(request));
            helper.setText(html, true);
            mailSender.send(mimeMessage);
        } catch (AddressException exception) {
            // Địa chỉ sai thì gửi lại bao nhiêu lần cũng sai: lỗi vĩnh viễn, vào DLQ ngay.
            throw new IllegalArgumentException("Invalid recipient address: " + request.recipient(), exception);
        } catch (MessagingException exception) {
            // Lỗi dựng email/SMTP có thể là tạm thời: để Spring Retry thử lại.
            throw new IllegalStateException("Cannot build email " + request.template(), exception);
        }
    }

    private String resolveSubject(EmailNotificationRequested request) {
        if (request.variables() != null && request.variables().containsKey("subject")) {
            return request.variables().get("subject");
        }
        String orderCode = request.variables() != null ? request.variables().getOrDefault("orderCode", "") : "";
        return switch (request.template()) {
            case "order-confirmed" -> "Xác nhận đơn hàng #" + orderCode + " - Fashion Store";
            case "order-cancelled" -> "Đơn hàng #" + orderCode + " đã bị hủy - Fashion Store";
            case "order-shipped" -> "Đơn hàng #" + orderCode + " đang được giao - Fashion Store";
            case "order-delivered" -> "Đơn hàng #" + orderCode + " đã giao thành công - Fashion Store";
            case "verify-email" -> "Xác nhận email - Fashion Store";
            default -> "Thông báo từ Fashion Store";
        };
    }
}
