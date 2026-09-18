package com.fashionstore.order.dto.dashboard;

import com.fashionstore.order.entity.enumeration.OrderStatus;
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