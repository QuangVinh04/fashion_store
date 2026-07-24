package com.fashionstore.notification.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@NoArgsConstructor
@Table(name = "processed_message")
public class ProcessedMessage {

    @Id
    @Column(name = "message_id", length = 64)
    private String messageId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ProcessedMessage(String messageId) {
        this.messageId = messageId;
        this.processedAt = Instant.now();
    }
}
