package com.fashionstore.catalog.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.catalog.entity.LegacyReturnRestockQuarantine;
import com.fashionstore.catalog.entity.enumeration.InventoryLedgerType;
import com.fashionstore.catalog.repository.*;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.inventory.command.RestockInventoryCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
@Service @RequiredArgsConstructor
public class LegacyReturnRestockGuard {
    private final LegacyReturnRestockQuarantineRepository quarantine;
    private final InventoryLedgerRepository ledger;
    private final ObjectMapper mapper;
    @Transactional
    public void quarantine(String messageId,EventEnvelope<?> envelope) {
        var command=mapper.convertValue(envelope.payload(),RestockInventoryCommand.class);
        String payload;try { payload=mapper.writeValueAsString(envelope); } catch(Exception invalid) { throw new IllegalArgumentException(invalid); }
        var existing=quarantine.findByMessageId(messageId);
        if(existing.isPresent()) {
            if(!existing.get().getOrderId().equals(command.orderId()) || !existing.get().getPayload().equals(payload)) throw new IllegalArgumentException("Legacy restock message payload conflict");
            return;
        }
        boolean executed=ledger.existsByRefOrderIdAndType(command.orderId(),InventoryLedgerType.RESTOCK);
        quarantine.saveAndFlush(LegacyReturnRestockQuarantine.builder().messageId(messageId).orderId(command.orderId()).payload(payload)
                .status(executed?"ALREADY_EXECUTED":"PENDING_REVIEW").receivedAt(Instant.now()).build());
    }
}
