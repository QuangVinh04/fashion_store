package com.fashionstore.notification.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.contracts.notification.EmailNotificationRequested;
import com.fashionstore.notification.config.RabbitMQConfig;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Component
@RequiredArgsConstructor
public class EmailNotificationListener {

    private final ObjectMapper objectMapper;
    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final ProcessedMessageRepository processedMessageRepository;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.EMAIL_QUEUE)
    public void handle(Message message) throws Exception {
        String messageId = message.getMessageProperties().getHeader("outboxEventId");
        if (messageId != null && processedMessageRepository.existsById(messageId)) {
            return;
        }

        JsonNode envelope = objectMapper.readTree(message.getBody());
        EmailNotificationRequested request = objectMapper.treeToValue(
                envelope.required("payload"),
                EmailNotificationRequested.class
        );
        send(request);

        if (messageId != null) {
            processedMessageRepository.save(new ProcessedMessage(messageId));
        }
    }

    private void send(EmailNotificationRequested request) throws Exception {
        Context context = new Context();
        request.variables().forEach(context::setVariable);
        String token = request.variables().getOrDefault("verifyCode", "");
        context.setVariable("verifyLink", frontendUrl + "/verify-email?token=" + token);

        String html = templateEngine.process(request.template(), context);
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setTo(request.recipient());
        helper.setSubject("Xác nhận email - Fashion Store");
        helper.setText(html, true);
        mailSender.send(mimeMessage);
    }
}
