package com.fashionstore.order.order.dto;

import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.order.order.entity.OrderStatus;

import lombok.Builder;

import java.time.LocalDate;

/** Bộ lọc danh sách đơn của admin. Field null hoặc rỗng nghĩa là không lọc theo field đó. */
@Builder
public record AdminOrderFilter(
        String userId,
        OrderStatus status,
        String search,
        PaymentMethod paymentMethod,
        LocalDate createdFrom,
        LocalDate createdTo) {}
