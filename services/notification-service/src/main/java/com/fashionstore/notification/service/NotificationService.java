package com.fashionstore.notification.service;

import com.fashionstore.contracts.notification.InAppNotificationRequested;
import com.fashionstore.notification.dto.NotificationSnapshot;

public interface NotificationService {
    void create(InAppNotificationRequested request);
    NotificationSnapshot list(int pageNo, int pageSize);
    void markRead(String id);
    void markAllRead();
}
