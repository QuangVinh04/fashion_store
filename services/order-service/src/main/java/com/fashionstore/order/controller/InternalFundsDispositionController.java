package com.fashionstore.order.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.exception.*;
import com.fashionstore.contracts.order.dto.OrderFundsDisposition;
import com.fashionstore.order.entity.enumeration.OrderSagaType;
import com.fashionstore.order.repository.*;

import lombok.RequiredArgsConstructor;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/orders")
@RequiredArgsConstructor
public class InternalFundsDispositionController {
    private final OrderRepository orders;
    private final OrderSagaRepository sagas;

    @GetMapping("/{id}/funds-disposition")
    @Transactional(readOnly = true)
    public ApiResponse<OrderFundsDisposition> get(@PathVariable String id) {
        var order =
                orders.findById(id)
                        .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        var saga = sagas.findByOrderIdAndType(id, OrderSagaType.CHECKOUT).orElse(null);
        return ApiResponse.<OrderFundsDisposition>builder()
                .data(
                        new OrderFundsDisposition(
                                id,
                                order.getUserId(),
                                order.getPaymentId(),
                                order.getStatus().name(),
                                order.getTotalAmount(),
                                order.getCurrency(),
                                saga == null ? null : saga.getInventoryReservationId()))
                .build();
    }
}
