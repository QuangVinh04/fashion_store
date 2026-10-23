package com.fashionstore.order.order.event;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.fashionstore.order.order.service.OrderReceiptService;
import com.fashionstore.order.returns.service.ReturnPickupService;
import com.fashionstore.order.returns.service.ReturnService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

class OrderAutoCompletionSchedulerTest {

    static final Instant NOW = Instant.parse("2026-10-12T01:00:00Z");
    final OrderReceiptService receipts = mock(OrderReceiptService.class);
    final ReturnService returns = mock(ReturnService.class);
    final ReturnPickupService pickups = mock(ReturnPickupService.class);
    final OrderAutoCompletionScheduler scheduler =
            new OrderAutoCompletionScheduler(receipts, returns, pickups, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void closesExpiredReturnsBeforeCompletingOrdersSoTheOrderCompletesInTheSamePass() {
        scheduler.complete();

        var order = inOrder(returns, pickups, receipts);
        order.verify(returns).expireUnsentReturns(NOW);
        order.verify(pickups).expirePickups(NOW);
        order.verify(pickups).reconcile(NOW);
        order.verify(pickups).publishSlaGauges(NOW);
        order.verify(receipts).completeExpired(NOW);
    }

    @Test
    void aFailureInTheGhnBackedStepsNeverStopsTheRestOfThePass() {
        doThrow(new IllegalStateException("ghn down")).when(pickups).expirePickups(NOW);
        doThrow(new IllegalStateException("ghn down")).when(pickups).reconcile(NOW);

        scheduler.complete();

        org.mockito.Mockito.verify(pickups).publishSlaGauges(NOW);
        org.mockito.Mockito.verify(receipts).completeExpired(NOW);
    }
}
