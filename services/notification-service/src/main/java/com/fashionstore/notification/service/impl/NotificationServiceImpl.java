package com.fashionstore.notification.service.impl;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.contracts.notification.InAppNotificationRequested;
import com.fashionstore.notification.dto.NotificationDto;
import com.fashionstore.notification.dto.NotificationSnapshot;
import com.fashionstore.notification.event.NotificationsChanged;
import com.fashionstore.notification.model.Notification;
import com.fashionstore.notification.repository.NotificationRepository;
import com.fashionstore.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationServiceImpl implements NotificationService {
    private final NotificationRepository repository;
    private final CurrentUserProvider currentUser;
    private final ApplicationEventPublisher events;

    @Override
    public void create(InAppNotificationRequested request) {
        if (request == null || blank(request.userId()) || request.userId().startsWith("anon:")
                || blank(request.type()) || blank(request.title()) || blank(request.message()) || blank(request.orderId())
                || request.userId().length() > 255 || request.type().length() > 64 || request.title().length() > 255
                || request.orderId().length() > 255) {
            throw new IllegalArgumentException("Invalid in-app notification request");
        }
        var notification = new Notification();
        notification.setUserId(request.userId());
        notification.setType(request.type());
        notification.setTitle(request.title());
        notification.setMessage(request.message());
        notification.setOrderId(request.orderId());
        repository.save(notification);
        events.publishEvent(new NotificationsChanged(request.userId()));
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationSnapshot list(int pageNo, int pageSize) {
        if (pageNo < 1 || pageSize < 1 || pageSize > 50) throw new AppException(ErrorCode.VALIDATION_FAILED);
        var userId = currentUser.getCurrentUserId();
        var page = repository.findByUserId(userId, PageRequest.of(pageNo - 1, pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        var items = page.getContent().stream().map(notification -> new NotificationDto(
                notification.getId(), notification.getType(), notification.getTitle(), notification.getMessage(),
                notification.getOrderId(), notification.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant(),
                notification.getReadAt())).toList();
        return new NotificationSnapshot(new PageResponse<>(pageNo, pageSize, page.getTotalPages(), items,
                page.getTotalElements()), repository.countByUserIdAndReadAtIsNull(userId));
    }

    @Override
    public void markRead(String id) {
        String userId = currentUser.getCurrentUserId();
        if (repository.markRead(id, userId, Instant.now()) > 0) {
            events.publishEvent(new NotificationsChanged(userId));
        } else if (!repository.existsByIdAndUserId(id, userId)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Override
    public void markAllRead() {
        String userId = currentUser.getCurrentUserId();
        if (repository.markAllRead(userId, Instant.now()) > 0) events.publishEvent(new NotificationsChanged(userId));
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
