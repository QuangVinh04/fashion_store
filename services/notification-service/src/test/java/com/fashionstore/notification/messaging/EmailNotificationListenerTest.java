package com.fashionstore.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.IContext;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailNotificationListenerTest {

    ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    JavaMailSender mailSender;

    @Mock
    SpringTemplateEngine templateEngine;

    @Mock
    ProcessedMessageRepository processedMessageRepository;

    EmailNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new EmailNotificationListener(objectMapper, mailSender, templateEngine, processedMessageRepository);
        ReflectionTestUtils.setField(listener, "frontendUrl", "http://localhost:3000");
    }

    @Test
    void handle_whenMessageAlreadyProcessed_skipsSending() throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setHeader("outboxEventId", "evt-123");
        Message message = new Message("{}".getBytes(), properties);

        when(processedMessageRepository.existsById("evt-123")).thenReturn(true);

        listener.handle(message);

        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(processedMessageRepository, never()).save(any());
    }

    @Test
    void handle_orderConfirmed_rendersTemplateAndSendsEmail() throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setHeader("outboxEventId", "evt-456");

        Map<String, Object> envelopeMap = Map.of(
                "payload", Map.of(
                        "recipient", "customer@test.com",
                        "template", "order-confirmed",
                        "variables", Map.of("orderCode", "ORD-12345")
                )
        );
        byte[] body = objectMapper.writeValueAsBytes(envelopeMap);
        Message message = new Message(body, properties);

        when(processedMessageRepository.existsById("evt-456")).thenReturn(false);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("order-confirmed"), any(IContext.class))).thenReturn("<html>Confirmed</html>");

        listener.handle(message);

        verify(mailSender).send(mimeMessage);
        assertThat(mimeMessage.getSubject()).isEqualTo("Xác nhận đơn hàng #ORD-12345 - Fashion Store");
        verify(processedMessageRepository).save(any(ProcessedMessage.class));
    }

    @Test
    void handle_orderShipped_setsCorrectSubject() throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setHeader("outboxEventId", "evt-789");

        Map<String, Object> envelopeMap = Map.of(
                "payload", Map.of(
                        "recipient", "customer@test.com",
                        "template", "order-shipped",
                        "variables", Map.of("orderCode", "ORD-777", "trackingCode", "GHN123")
                )
        );
        byte[] body = objectMapper.writeValueAsBytes(envelopeMap);
        Message message = new Message(body, properties);

        when(processedMessageRepository.existsById("evt-789")).thenReturn(false);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("order-shipped"), any(IContext.class))).thenReturn("<html>Shipped</html>");

        listener.handle(message);

        verify(mailSender).send(mimeMessage);
        assertThat(mimeMessage.getSubject()).isEqualTo("Đơn hàng #ORD-777 đang được giao - Fashion Store");
    }

    @Test
    void handle_orderDelivered_setsCorrectSubject() throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setHeader("outboxEventId", "evt-999");

        Map<String, Object> envelopeMap = Map.of(
                "payload", Map.of(
                        "recipient", "customer@test.com",
                        "template", "order-delivered",
                        "variables", Map.of("orderCode", "ORD-888")
                )
        );
        byte[] body = objectMapper.writeValueAsBytes(envelopeMap);
        Message message = new Message(body, properties);

        when(processedMessageRepository.existsById("evt-999")).thenReturn(false);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("order-delivered"), any(IContext.class))).thenReturn("<html>Delivered</html>");

        listener.handle(message);

        verify(mailSender).send(mimeMessage);
        assertThat(mimeMessage.getSubject()).isEqualTo("Đơn hàng #ORD-888 đã giao thành công - Fashion Store");
    }
}
