package com.fashionstore.catalog.inventory.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.catalog.inventory.service.InventoryService;
import com.fashionstore.common.messaging.RabbitTopology;
import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.contracts.common.*;
import com.fashionstore.contracts.inventory.command.CancelOrderInventoryCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component @RequiredArgsConstructor
public class OrderInventoryCancellationListener {
    public static final String QUEUE="catalog.order-inventory-cancellation";
    private final InventoryService inventoryService;
    private final ProcessedMessageService processed;
    private final ObjectMapper mapper;

    @Transactional @RabbitListener(queues=QUEUE)
    public void onCommand(EventEnvelope<?> envelope,@Header(RabbitTopology.OUTBOX_EVENT_ID_HEADER) String messageId) {
        if(!EventTypes.ORDER_INVENTORY_CANCELLATION_REQUESTED.equals(envelope.eventType())) throw new IllegalArgumentException("Unsupported inventory cancellation command");
        processed.processOnce(messageId,"catalog.order-inventory-cancellation.v1",()->
                inventoryService.cancelUnshippedOrder(mapper.convertValue(envelope.payload(),CancelOrderInventoryCommand.class),envelope.correlationId()));
    }
}
