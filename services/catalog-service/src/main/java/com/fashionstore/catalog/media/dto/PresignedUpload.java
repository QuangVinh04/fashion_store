package com.fashionstore.catalog.media.dto;

/** URL da ky de browser PUT thang len storage. */
public record PresignedUpload(
        String url,
        int expiresInSeconds
) {
}
