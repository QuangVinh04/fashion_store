package com.fashionstore.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code endpoint} la dia chi trong mang docker (service goi statObject/removeObject),
 * {@code publicEndpoint} la dia chi browser nhin thay. Presigned URL phai duoc ky bang
 * publicEndpoint: doi host sau khi ky se lam hong chu ky.
 */
@ConfigurationProperties(prefix = "app.minio")
public record MinioProperties(
        String endpoint,
        String publicEndpoint,
        String accessKey,
        String secretKey,
        String bucket,
        int presignExpirySeconds,
        long maxUploadBytes
) {
    public MinioProperties {
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "http://localhost:9000";
        }
        if (publicEndpoint == null || publicEndpoint.isBlank()) {
            publicEndpoint = endpoint;
        }
        if (bucket == null || bucket.isBlank()) {
            bucket = "fashion-media";
        }
        if (presignExpirySeconds <= 0) {
            presignExpirySeconds = 900;
        }
        if (maxUploadBytes <= 0) {
            maxUploadBytes = 20L * 1024 * 1024;
        }
    }
}
