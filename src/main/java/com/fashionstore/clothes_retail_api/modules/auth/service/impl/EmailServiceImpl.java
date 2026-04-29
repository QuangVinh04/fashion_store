package com.fashionstore.clothes_retail_api.modules.auth.service.impl;

import com.fashionstore.clothes_retail_api.modules.auth.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;


@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EmailServiceImpl implements EmailService {

    JavaMailSender mailSender;
    SpringTemplateEngine templateEngine;


    @NonFinal
    @Value("${app.frontend-url}")
    String frontendUrl;

    @Override
    public void sendVerificationEmail(String to, String fullName, String token) {
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
    }
}
