package com.fashionstore.order.order.dto;

import com.fashionstore.order.order.entity.OrderStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusCountResponse {
    private OrderStatus status;
    private Long count;
}
