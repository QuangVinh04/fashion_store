package com.fashionstore.catalog.event;

import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.catalog.config.RabbitMQNames;
import com.fashionstore.catalog.service.InventoryService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductVariantStockEventListener {

    InventoryService inventoryService;
    ProcessedMessageService processedMessageService;

    @Transactional
    @RabbitListener(queues = RabbitMQNames.INVENTORY_PRODUCT_VARIANT_STOCK_QUEUE)
    public void handle(ProductVariantStockEvent event,
                       @Header(RabbitMQNames.OUTBOX_EVENT_ID_HEADER) String messageId) {
        processedMessageService.processOnce(messageId, RabbitMQNames.INVENTORY_STOCK_CONSUMER, () -> process(event));
    }

    private void process(ProductVariantStockEvent event) {
        if (event.action() == ProductVariantStockEvent.Action.DELETE) {
            inventoryService.deleteStock(event.variantId());
            return;
        }

        inventoryService.upsertStock(event.variantId(), event.quantity());
    }
}
