package com.fashionstore.order.service;

import com.fashionstore.order.dto.*;
import com.fashionstore.order.entity.*;
import com.fashionstore.order.entity.enumeration.*;
import com.fashionstore.order.repository.*;

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
            eligible =
                    shipment == null
                            || shipment.getHandedOffAt() == null
                                    && Set.of(ShipmentStatus.PENDING, ShipmentStatus.CANCELLED)
                                            .contains(shipment.getStatus());
        }
        response.setCanCancel(eligible);
        return response;
    }
}
