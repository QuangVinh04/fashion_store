package com.fashionstore.order.saga.event;

import com.fashionstore.order.saga.entity.OrderSagaStatus;
import com.fashionstore.order.saga.entity.OrderSagaType;
import com.fashionstore.order.saga.repository.OrderSagaRepository;
import com.fashionstore.order.saga.repository.SagaTimeoutCandidate;
import com.fashionstore.order.saga.service.OrderCancellationService;
import com.fashionstore.order.saga.service.OrderSagaTimeoutService;

import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** Scan/dispatch only; each service locks and re-checks in its own transaction. */
@Component
@RequiredArgsConstructor
public class OrderSagaTimeoutScanner {
    private final OrderSagaRepository sagas;
    private final OrderSagaTimeoutService checkout;
    private final OrderCancellationService cancellation;

    @Scheduled(fixedDelayString = "${app.saga.timeout-scan-delay-ms:15000}")
    public void expireDueSteps() {
        var now = LocalDateTime.now();
        var candidates =
                sagas.findDueCandidates(
                        List.of(OrderSagaStatus.RUNNING, OrderSagaStatus.COMPENSATING), now);
        candidates.stream()
                .sorted(
                        Comparator.comparing(SagaTimeoutCandidate::orderId)
                                .thenComparing(SagaTimeoutCandidate::id))
                .forEach(
                        candidate -> {
                            if (candidate.type() == OrderSagaType.CHECKOUT)
                                checkout.expireCheckout(candidate.orderId(), candidate.id(), now);
                            else if (candidate.type() == OrderSagaType.CANCELLATION)
                                cancellation.onTimeout(candidate.orderId(), candidate.id(), now);
                        });
    }
}
