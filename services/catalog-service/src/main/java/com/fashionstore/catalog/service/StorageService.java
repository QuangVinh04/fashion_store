package com.fashionstore.catalog.service;

import com.fashionstore.catalog.dto.PresignedUpload;
import com.fashionstore.catalog.dto.StoredObject;
import java.util.Map;

public interface StorageService {

    PresignedUpload presignUpload(String storageKey, String contentType);

    PresignedUpload presignUpload(String storageKey, String contentType, Map<String, String> requiredHeaders);

    String presignDownload(String storageKey);

    /** {@code null} khi object chua ton tai tren storage. */
    StoredObject stat(String storageKey);

    /** Doc toi da {@code maxBytes} byte dau tien cua object de nhan dien magic bytes / signature. */
    byte[] readPrefix(String storageKey, int maxBytes);

    void delete(String storageKey);
}
