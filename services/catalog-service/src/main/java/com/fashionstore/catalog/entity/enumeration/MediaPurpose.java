package com.fashionstore.catalog.entity.enumeration;

public enum MediaPurpose {
    GENERAL,
    AVATAR,
    RETURN_EVIDENCE,
    PAYMENT_EVIDENCE;

    public boolean isEvidence() { return this == RETURN_EVIDENCE || this == PAYMENT_EVIDENCE; }
}
