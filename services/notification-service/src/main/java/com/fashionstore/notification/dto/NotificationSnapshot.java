package com.fashionstore.notification.dto;

import com.fashionstore.common.dto.PageResponse;
import java.util.List;

public record NotificationSnapshot(PageResponse<List<NotificationDto>> page, long unreadCount) {
}
