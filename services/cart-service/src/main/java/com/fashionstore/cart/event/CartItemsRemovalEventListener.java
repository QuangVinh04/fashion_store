package com.fashionstore.cart.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.cart.config.RabbitMQConfig;
import com.fashionstore.cart.repository.CartItemRepository;
import com.fashionstore.contracts.EventEnvelope;
import com.fashionstore.contracts.cart.CartItemsRemovalRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CartItemsRemovalEventListener {

    private final CartItemRepository cartItemRepository;
    private final ProcessedMessageService processedMessageService;
    private final ObjectMapper objectMapper;

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.CART_ITEMS_REMOVAL_QUEUE)
    public void handle(
            EventEnvelope<?> envelope,
            @Header(RabbitMQConfig.OUTBOX_EVENT_ID_HEADER) String messageId
    ) {
        processedMessageService.processOnce(messageId, "cart-items-removal-v1", () -> {
            CartItemsRemovalRequested request = objectMapper.convertValue(
                    envelope.payload(),
                    CartItemsRemovalRequested.class
            );
            if (!request.cartItemIds().isEmpty()) {
                cartItemRepository.deleteOwnedItems(request.userId(), request.cartItemIds());
            }
        });
    }
}
