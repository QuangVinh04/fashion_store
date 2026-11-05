package com.fashionstore.identity.messaging;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.identity.event.KeycloakUserEvent;
import com.fashionstore.identity.config.messaging.RabbitMQConfig;
import com.fashionstore.identity.service.UserProvisioningService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Event của Keycloak (Event Listener SPI) -> tạo / đồng bộ dòng {@code users} ngay, không đợi request đầu tiên của user.
 * Event có thể mất (SPI không có outbox) nên JIT provisioning ở request đầu tiên vẫn là đường dự phòng.
 * Xử lý idempotent (xem {@link UserProvisioningService#provision(String, String, String, boolean)}) nên không cần bảng processed-message.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakUserEventListener {

    private final UserProvisioningService provisioningService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConfig.KEYCLOAK_USER_EVENTS_QUEUE)
    public void onKeycloakUserEvent(EventEnvelope<?> envelope) {
        if (!EventTypes.KEYCLOAK_USER_REGISTERED.equals(envelope.eventType())
                && !EventTypes.KEYCLOAK_USER_EMAIL_UPDATED.equals(envelope.eventType())) {
            // Lỗi vĩnh viễn: retry không giúp gì, chuyển thẳng sang DLQ
            throw new AmqpRejectAndDontRequeueException("Unsupported eventType on Keycloak user queue: " + envelope.eventType());
        }
        KeycloakUserEvent event = objectMapper.convertValue(envelope.payload(), KeycloakUserEvent.class);
        try {
            // Email mới đăng ký / vừa đổi luôn cần xác minh lại, nên emailVerified = false
            provisioningService.provision(event.userId(), event.email(), fullName(event), false);
        } catch (AppException e) {
            // Vd email đã thuộc user khác (ACCOUNT_LINK_CONFLICT): cần người xử lý, không phải lỗi tạm thời
            log.warn("Keycloak event {} for user {} rejected: {}", envelope.eventType(), event.userId(), e.getMessage());
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
    }

    private static String fullName(KeycloakUserEvent event) {
        String name = Stream.of(event.firstName(), event.lastName())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" "));
        return name.isEmpty() ? null : name;
    }
}
