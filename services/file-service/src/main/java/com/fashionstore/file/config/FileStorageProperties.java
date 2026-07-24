package com.fashionstore.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record FileStorageProperties(
        String location,
        String publicBaseUrl
) {
    public FileStorageProperties {
        if (location == null || location.isBlank()) {
            location = "./storage/file-service";
        }
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            publicBaseUrl = "http://localhost:8091";
        }
    }
}
