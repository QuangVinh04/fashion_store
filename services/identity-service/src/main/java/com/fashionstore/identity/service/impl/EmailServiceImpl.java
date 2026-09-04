package com.fashionstore.identity.service.impl;

import com.fashionstore.identity.service.EmailService;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.notification.EmailNotificationRequested;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Map;


@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void sendVerificationEmail(String to, String fullName, String token) {
        EmailNotificationRequested payload = new EmailNotificationRequested(
                to,
                "verify-email",
                Map.of(
                        "fullName", fullName,
                        "verifyCode", token,
                        "expireHours", "24"
                )
        );
        eventPublisher.publishEvent(EventEnvelope.v1(
                EventTypes.NOTIFICATION_EMAIL_REQUESTED,
                to,
                MDC.get("correlationId"),
                payload
        ));
        /*
        try {
            // Render Thymeleaf template → HTML string
            Context context = new Context();
            context.setVariable("fullName", fullName);
            context.setVariable("verifyLink", frontendUrl + "/verify-email?token=" + token);
            context.setVariable("verifyCode", token);
            context.setVariable("expireHours", 24);
            String htmlContent = templateEngine.process("verify-email", context);
            // Gửi MimeMessage (HTML)
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject("Xác nhận email - Fashion Store");
            helper.setText(htmlContent, true); // true = HTML
            mailSender.send(message);
            log.info("Verification email sent to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send verification email to: {}", to, e);
            throw new RuntimeException("Không thể gửi email xác nhận", e);
        }
        */
    }
}
