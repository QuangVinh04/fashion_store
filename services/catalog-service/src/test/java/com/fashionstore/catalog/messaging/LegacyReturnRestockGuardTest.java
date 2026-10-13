package com.fashionstore.catalog.messaging;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.catalog.service.*;
import com.fashionstore.catalog.entity.*;
import com.fashionstore.catalog.entity.enumeration.InventoryLedgerType;
import com.fashionstore.catalog.repository.*;
import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.contracts.common.*;
import com.fashionstore.contracts.inventory.command.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LegacyReturnRestockGuardTest {
    @Test void legacyPendingReturnRestockMustNeverMutateInventory() {
        var f=new Fixture(false);f.listener.onRestockRequested(f.event,"message");
        verifyNoInteractions(f.inventory);assertThat(f.saved.get("message").getStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(f.saved.get("message").getPayload()).contains("inventory.restock.requested");
    }
    @Test void duplicateLegacyMessageKeepsOneAudit() {
        var f=new Fixture(false);f.listener.onRestockRequested(f.event,"message");f.listener.onRestockRequested(f.event,"message");
        verify(f.quarantine).saveAndFlush(any());verifyNoInteractions(f.inventory);assertThat(f.saved).hasSize(1);
    }
    @Test void alreadyExecutedStockIsRecordedAndNeverReversed() {
        var f=new Fixture(true);f.listener.onRestockRequested(f.event,"message");
        assertThat(f.saved.get("message").getStatus()).isEqualTo("ALREADY_EXECUTED");verifyNoInteractions(f.inventory);
    }
    @Test void cancellationReservationReleaseRemainsSeparate() {
        var f=new Fixture(false);var command=new ReleaseInventoryCommand("order","reservation");
        f.listener.onReleaseRequested(EventEnvelope.v1(EventTypes.INVENTORY_RELEASE_REQUESTED,"order","saga",command),"release-message");
        verify(f.inventory).releaseSaga(command,"saga");verifyNoInteractions(f.quarantine);
    }
    static class Fixture {
        InventoryService inventory=mock(InventoryService.class);
        LegacyReturnRestockQuarantineRepository quarantine=mock(LegacyReturnRestockQuarantineRepository.class);
        Map<String,LegacyReturnRestockQuarantine> saved=new HashMap<>();
        InventoryCommandListener listener;
        EventEnvelope<?> event=EventEnvelope.v1(EventTypes.INVENTORY_RESTOCK_REQUESTED,"order","order",new RestockInventoryCommand("order"));
        Fixture(boolean executed) {
            var ledger=mock(InventoryLedgerRepository.class);var processed=mock(ProcessedMessageService.class);var mapper=new ObjectMapper().findAndRegisterModules();
            when(quarantine.findByMessageId(anyString())).thenAnswer(inv -> Optional.ofNullable(saved.get(inv.getArgument(0))));
            when(quarantine.saveAndFlush(any())).thenAnswer(inv -> { LegacyReturnRestockQuarantine row=inv.getArgument(0);saved.put(row.getMessageId(),row);return row; });
            when(ledger.existsByRefOrderIdAndType("order",InventoryLedgerType.RESTOCK)).thenReturn(executed);
            doAnswer(inv -> { inv.getArgument(2,Runnable.class).run();return null; }).when(processed).processOnce(anyString(),anyString(),any(Runnable.class));
            listener=new InventoryCommandListener(inventory,processed,mapper,new LegacyReturnRestockGuard(quarantine,ledger,mapper));
        }
    }
}
