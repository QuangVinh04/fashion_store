package com.fashionstore.catalog.media.entity;

public enum MediaPurpose {
    GENERAL,
    AVATAR,
    RETURN_EVIDENCE,
    PAYMENT_EVIDENCE;

    public boolean isEvidence() { return this == RETURN_EVIDENCE || this == PAYMENT_EVIDENCE; }
}
