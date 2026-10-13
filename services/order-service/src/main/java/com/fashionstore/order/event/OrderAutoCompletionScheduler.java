package com.fashionstore.order.event;

import com.fashionstore.order.service.OrderReceiptService;
import com.fashionstore.order.service.ReturnService;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.order.auto-completion-enabled", havingValue = "true")
public class OrderAutoCompletionScheduler {
    private final OrderReceiptService service;
    private final ReturnService returns;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.order.auto-completion-delay-ms:60000}")
    public void complete() {
        var now = clock.instant();
        // Hủy yêu cầu trả hàng quá hạn gửi hàng trước, để đơn được hoàn tất ngay trong lượt này
        returns.expireUnsentReturns(now);
        service.completeExpired(now);
    }
}
