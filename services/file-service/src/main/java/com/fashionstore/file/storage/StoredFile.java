package com.fashionstore.file.storage;

public record StoredFile(
        String storageKey,
        String storedFilename,
        String extension
) {
}
