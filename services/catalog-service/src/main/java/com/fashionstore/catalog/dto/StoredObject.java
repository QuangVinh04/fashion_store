package com.fashionstore.catalog.dto;

/** Trang thai that cua object tren storage, doc bang statObject. */
public record StoredObject(
        long sizeBytes,
        String contentType,
        String etag
) {
}
