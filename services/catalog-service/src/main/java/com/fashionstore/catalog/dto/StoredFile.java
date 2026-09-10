package com.fashionstore.catalog.dto;

public record StoredFile(
        String storageKey,
        String storedFilename,
        String extension
) {
}
