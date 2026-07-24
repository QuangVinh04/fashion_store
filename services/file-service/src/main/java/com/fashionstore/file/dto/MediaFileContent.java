package com.fashionstore.file.dto;

import org.springframework.core.io.Resource;

public record MediaFileContent(
        Resource resource,
        String contentType,
        String filename
) {
}
