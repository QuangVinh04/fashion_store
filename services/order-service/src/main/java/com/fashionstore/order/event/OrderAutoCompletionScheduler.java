package com.fashionstore.order.event;

import com.fashionstore.order.service.OrderReceiptService;
import com.fashionstore.order.service.ReturnPickupService;
import com.fashionstore.order.service.ReturnService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.order.auto-completion-enabled", havingValue = "true")
public class OrderAutoCompletionScheduler {
    private final OrderReceiptService service;
    private final ReturnService returns;
    private final ReturnPickupService pickups;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.order.auto-completion-delay-ms:60000}")
    public void complete() {
        var now = clock.instant();
        // Đóng yêu cầu trả hàng quá hạn trước, để đơn được hoàn tất ngay trong lượt này
        returns.expireUnsentReturns(now);
        // Các bước gọi GHN không được làm hỏng phần còn lại của lượt quét
        guarded("expirePickups", () -> pickups.expirePickups(now));
        guarded("reconcile", () -> pickups.reconcile(now));
        guarded("publishSlaGauges", () -> pickups.publishSlaGauges(now));
        service.completeExpired(now);
    }

    private void guarded(String step, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            log.error("[Return] Scheduled step {} failed", step, failure);
        }
    }
}
