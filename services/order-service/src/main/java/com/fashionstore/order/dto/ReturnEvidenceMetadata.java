package com.fashionstore.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Metadata used to verify ownership and purpose of private return evidence. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReturnEvidenceMetadata(
        String id,
        String ownerId,
        String purpose,
        String status,
        String visibility,
        String contentType) {
}
