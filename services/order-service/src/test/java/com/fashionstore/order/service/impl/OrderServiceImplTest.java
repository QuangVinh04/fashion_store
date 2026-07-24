package com.fashionstore.order.service.impl;

import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.order.dto.CreateOrderRequest;
import com.fashionstore.order.dto.OrderResponse;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.OrderStatus;
import com.fashionstore.order.repository.CheckoutRepository;
import com.fashionstore.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CheckoutRepository checkoutRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl(
                orderRepository,
                checkoutRepository,
                currentUserProvider,
                eventPublisher
        );
    }

    @Test
    void returnsExistingOrderWhenDefaultCheckoutIdempotencyKeyWasAlreadyUsed() {
        Order existing = Order.builder()
                .orderCode("ORD-2026-EXISTING")
                .userId("user-1")
                .idempotencyKey("checkout-1")
                .status(OrderStatus.PENDING_INVENTORY)
                .recipientName("Customer")
                .recipientPhone("0900000000")
                .shippingAddress("Address")
                .subtotalAmount(BigDecimal.TEN)
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .totalAmount(BigDecimal.TEN)
                .build();
        existing.setId("order-1");

        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
        when(orderRepository.findByUserIdAndIdempotencyKey("user-1", "checkout-1"))
                .thenReturn(Optional.of(existing));

        OrderResponse response = service.placeOrder(
                "checkout-1",
                null,
                new CreateOrderRequest()
        );

        assertEquals("order-1", response.getId());
        assertEquals("checkout-1", response.getCheckoutId());
        assertEquals(OrderStatus.PENDING_INVENTORY, response.getStatus());
        verify(checkoutRepository, never()).findForUpdateByIdAndUserId("checkout-1", "user-1");
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }
}
