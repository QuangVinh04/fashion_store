package com.fashionstore.catalog.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.catalog.service.InventoryService;
import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.inventory.command.ConfirmInventoryCommand;
import com.fashionstore.contracts.inventory.command.ReleaseInventoryCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InventoryCommandListenerTest {

    @Mock
    private InventoryService inventoryService;

    @Mock
    private ProcessedMessageService processedMessageService;

    private InventoryCommandListener listener;

    @BeforeEach
    void setUp() {
        listener = new InventoryCommandListener(inventoryService, processedMessageService, new ObjectMapper());
        doAnswer(invocation -> {
            invocation.getArgument(2, Runnable.class).run();
            return null;
        }).when(processedMessageService).processOnce(anyString(), anyString(), any(Runnable.class));
    }

    @Test
    void confirmationPassesIncomingSagaCorrelationIdToService() {
        ConfirmInventoryCommand command = new ConfirmInventoryCommand("order-1", "reservation-1");
        EventEnvelope<ConfirmInventoryCommand> request = EventEnvelope.v1(
                EventTypes.INVENTORY_CONFIRMATION_REQUESTED, "order-1", "saga-1", command);

        listener.onConfirmationRequested(request, "message-1");

        verify(inventoryService).confirmSaga(command, "saga-1");
    }

    @Test
    void releasePassesIncomingSagaCorrelationIdToService() {
        ReleaseInventoryCommand command = new ReleaseInventoryCommand("order-2", "reservation-2");
        EventEnvelope<ReleaseInventoryCommand> request = EventEnvelope.v1(
                EventTypes.INVENTORY_RELEASE_REQUESTED, "order-2", "saga-2", command);

        listener.onReleaseRequested(request, "message-2");

        verify(inventoryService).releaseSaga(command, "saga-2");
    }
}
