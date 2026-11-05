package com.fashionstore.catalog.media.entity;

public enum MediaPurpose {
    GENERAL,
    AVATAR,
    REVIEW_IMAGE,
    RETURN_EVIDENCE,
    PAYMENT_EVIDENCE;

    public boolean isEvidence() { return this == RETURN_EVIDENCE || this == PAYMENT_EVIDENCE; }

    /** Ảnh khách upload: chỉ nhận ảnh nhỏ và kiểm tra chữ ký file. */
    public boolean isCustomerImage() { return this != GENERAL; }
}
