package com.fashionstore.notification.messaging;

import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.IContext;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailNotificationListenerTest {

    @Mock
    JavaMailSender mailSender;
    @Mock
    SpringTemplateEngine templateEngine;
    @Mock
    ProcessedMessageService processedMessageService;

    EmailNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new EmailNotificationListener(new ObjectMapper(), mailSender, templateEngine, processedMessageService);
        ReflectionTestUtils.setField(listener, "frontendUrl", "http://localhost:3000");
    }

    @Test
    void duplicateMessageIsSkippedByTheSharedIdempotencyGuard() throws Exception {
        // processOnce không chạy action => message này đã được xử lý trước đó.
        listener.handle(envelope("order-confirmed", Map.of("orderCode", "ORD-1")), "evt-123");

        verify(processedMessageService).processOnce(eq("evt-123"), eq(EmailNotificationListener.CONSUMER_NAME), any());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void orderConfirmedRendersTemplateAndSendsEmail() throws Exception {
        MimeMessage mimeMessage = runOnce("order-confirmed", Map.of("orderCode", "ORD-12345"), "<html>Confirmed</html>");

        verify(mailSender).send(mimeMessage);
        assertThat(mimeMessage.getSubject()).isEqualTo("Xác nhận đơn hàng #ORD-12345 - Fashion Store");
    }

    @Test
    void orderShippedSetsCorrectSubject() throws Exception {
        MimeMessage mimeMessage = runOnce("order-shipped", Map.of("orderCode", "ORD-777", "trackingCode", "GHN123"), "<html>Shipped</html>");

        assertThat(mimeMessage.getSubject()).isEqualTo("Đơn hàng #ORD-777 đang được giao - Fashion Store");
    }

    @Test
    void orderDeliveredSetsCorrectSubject() throws Exception {
        MimeMessage mimeMessage = runOnce("order-delivered", Map.of("orderCode", "ORD-888"), "<html>Delivered</html>");

        assertThat(mimeMessage.getSubject()).isEqualTo("Đơn hàng #ORD-888 đã giao thành công - Fashion Store");
    }

    @Test
    void orderCancelledSetsCorrectSubject() throws Exception {
        MimeMessage mimeMessage = runOnce("order-cancelled", Map.of("orderCode", "ORD-999", "reason", "Hết hàng"), "<html>Cancelled</html>");

        verify(mailSender).send(mimeMessage);
        assertThat(mimeMessage.getSubject()).isEqualTo("Đơn hàng #ORD-999 đã bị hủy - Fashion Store");
    }

    @Test
    void wrongEventTypeOnEmailQueueIsRejectedAsPermanentFailure() {
        EventEnvelope<Map<String, Object>> wrong = EventEnvelope.v1("order.confirmed", "order-1", null, Map.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> listener.handle(wrong, "evt-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private MimeMessage runOnce(String template, Map<String, String> variables, String html) throws Exception {
        doAnswer(invocation -> {
            invocation.getArgument(2, Runnable.class).run();
            return null;
        }).when(processedMessageService).processOnce(anyString(), anyString(), any(Runnable.class));
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq(template), any(IContext.class))).thenReturn(html);

        listener.handle(envelope(template, variables), "evt-" + template);
        return mimeMessage;
    }

    /** Giống hệt cái Jackson2JsonMessageConverter dựng ra từ JSON: payload là Map, chưa phải record. */
    private static EventEnvelope<Map<String, Object>> envelope(String template, Map<String, String> variables) {
        return EventEnvelope.v1(EventTypes.NOTIFICATION_EMAIL_REQUESTED, "order-1", null, Map.of(
                "recipient", "customer@test.com",
                "template", template,
                "variables", variables));
    }
}
