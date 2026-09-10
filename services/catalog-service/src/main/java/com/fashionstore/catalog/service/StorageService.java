package com.fashionstore.catalog.service;

import com.fashionstore.catalog.dto.StoredFile;
import org.springframework.core.io.Resource;

public interface StorageService {

    StoredFile store(byte[] content, String originalFilename);

    Resource load(String storageKey);

    void delete(String storageKey);
}
