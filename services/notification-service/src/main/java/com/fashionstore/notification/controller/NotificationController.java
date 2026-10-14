package com.fashionstore.notification.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.notification.dto.NotificationSnapshot;
import com.fashionstore.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService service;

    @GetMapping
    public ApiResponse<NotificationSnapshot> list(@RequestParam(defaultValue = "1") int pageNo,
                                                 @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.<NotificationSnapshot>builder().data(service.list(pageNo, pageSize)).build();
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable String id) {
        service.markRead(id);
        return ApiResponse.<Void>builder().build();
    }

    @PatchMapping("/read-all")
    public ApiResponse<Void> markAllRead() {
        service.markAllRead();
        return ApiResponse.<Void>builder().build();
    }
}
