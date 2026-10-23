package com.fashionstore.catalog.media.dto;

/** Trang thai that cua object tren storage, doc bang statObject. */
public record StoredObject(
        long sizeBytes,
        String contentType,
        String etag
) {
}
