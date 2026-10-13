package com.fashionstore.contracts.inventory.event;

/** Catalog verified this exact reservation was released/rejected before stock commit. */
public record InventoryConfirmationRejectedEvent(String orderId,String reservationId,String code,String reason) {}
