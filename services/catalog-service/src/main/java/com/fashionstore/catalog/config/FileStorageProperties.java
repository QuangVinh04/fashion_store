package com.fashionstore.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record FileStorageProperties(
        String publicBaseUrl
) {
    public FileStorageProperties {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            publicBaseUrl = "http://localhost:8087";
        }
    }
}
