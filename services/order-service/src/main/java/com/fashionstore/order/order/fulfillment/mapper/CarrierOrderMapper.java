package com.fashionstore.order.order.fulfillment.mapper;

import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.order.order.entity.Order;
import com.fashionstore.order.shipment.dto.CarrierOrder;

import java.math.BigDecimal;
import java.util.List;

/** Maps an order onto the carrier-neutral shape the shipment module understands. */
public final class CarrierOrderMapper {

    private CarrierOrderMapper() {
    }

    public static CarrierOrder toCarrierOrder(Order order) {
        List<CarrierOrder.Item> items = order.getItems().stream()
                .map(item -> new CarrierOrder.Item(
                        item.getProductName(), item.getVariantId(), item.getQuantity(), item.getUnitPrice()))
                .toList();
        BigDecimal codAmount = order.getPaymentMethod() == PaymentMethod.COD ? order.getTotalAmount() : BigDecimal.ZERO;
        return new CarrierOrder(
                order.getAddress().getRecipientName(),
                order.getAddress().getRecipientPhone(),
                order.getShippingAddress(),
                codAmount,
                items);
    }
}
