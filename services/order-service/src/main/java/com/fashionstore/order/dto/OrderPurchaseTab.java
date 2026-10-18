package com.fashionstore.order.dto;

/** Customer purchase-list filters; independent of persisted workflow states. */
public enum OrderPurchaseTab {
    PENDING, PREPARING, SHIPPING, COMPLETED, CANCELLED, RETURNS
}
