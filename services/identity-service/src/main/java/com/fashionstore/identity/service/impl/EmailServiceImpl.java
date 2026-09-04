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
    }
}
