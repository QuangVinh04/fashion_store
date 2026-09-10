package com.fashionstore.catalog.service;

import com.fashionstore.catalog.dto.PresignedUpload;
import com.fashionstore.catalog.dto.StoredObject;

public interface StorageService {

    PresignedUpload presignUpload(String storageKey, String contentType);

    String presignDownload(String storageKey);

    /** {@code null} khi object chua ton tai tren storage. */
    StoredObject stat(String storageKey);

    void delete(String storageKey);
}
