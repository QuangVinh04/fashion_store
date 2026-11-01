package com.fashionstore.catalog.media.service;

import com.fashionstore.catalog.media.dto.PresignedUpload;
import com.fashionstore.catalog.media.dto.StoredObject;
import java.util.Map;

public interface StorageService {

    PresignedUpload presignUpload(String storageKey, String contentType);

    PresignedUpload presignUpload(String storageKey, String contentType, Map<String, String> requiredHeaders);

    String presignDownload(String storageKey);

    /** {@code null} khi object chua ton tai tren storage. */
    StoredObject stat(String storageKey);

    /** Doc toi da {@code maxBytes} byte dau tien cua object de nhan dien magic bytes / signature. */
    byte[] readPrefix(String storageKey, int maxBytes);

    /** Doc toan bo object; dung cho object nho da qua gioi han upload (vd tao thumbnail). */
    byte[] read(String storageKey);

    void put(String storageKey, byte[] content, String contentType);

    void delete(String storageKey);
}
