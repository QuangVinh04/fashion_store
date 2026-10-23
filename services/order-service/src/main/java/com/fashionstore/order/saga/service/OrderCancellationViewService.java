package com.fashionstore.order.saga.service;

import com.fashionstore.order.saga.dto.OrderCancellationResponse;
import com.fashionstore.order.order.dto.OrderResponse;
import com.fashionstore.order.order.entity.Order;
import com.fashionstore.order.saga.entity.OrderSaga;
import com.fashionstore.order.shipment.entity.Shipment;
import com.fashionstore.order.saga.entity.OrderSagaType;
import com.fashionstore.order.order.entity.OrderStatus;
import com.fashionstore.order.saga.repository.OrderSagaRepository;
import com.fashionstore.order.order.repository.OrderStatusHistoryRepository;
import com.fashionstore.order.shipment.repository.ShipmentRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderCancellationViewService {
    private final OrderSagaRepository sagas;
    private final ShipmentRepository shipments;
    private final OrderStatusHistoryRepository history;

    @Transactional(readOnly = true)
    public OrderResponse enrich(Order order, OrderResponse response) {
        OrderSaga cancellation =
                sagas.findByOrderIdAndType(order.getId(), OrderSagaType.CANCELLATION).orElse(null);
        response.setCancellation(
                cancellation == null
                        ? null
                        : OrderCancellationResponse.from(
                                cancellation,
                                order,
                                history.findByOrderIdOrderByCreatedAtDescIdDesc(order.getId())));
        boolean eligible =
                cancellation == null
                        && Set.of(
                                        OrderStatus.CONFIRMED,
                                        OrderStatus.PROCESSING,
                                        OrderStatus.PACKED)
                                .contains(order.getStatus());
        if (eligible) {
            Shipment shipment = shipments.findByOrderId(order.getId()).orElse(null);
            eligible = shipment == null || !shipment.isHandedOff();
        }
        if (order.getStatus() == OrderStatus.PENDING) {
            eligible =
                    sagas.findByOrderIdAndType(order.getId(), OrderSagaType.CHECKOUT)
                            .map(SagaCancellationService::cancellable)
                            .orElse(false);
        }
        response.setCanCancel(eligible);
        return response;
    }
}
