package com.fashionstore.catalog.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    /** Client noi bo: stat, remove — chay tu trong mang docker. */
    @Bean
    MinioClient minioClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    /** Client dung de ky presigned URL, phai tro toi host ma browser goi duoc. */
    @Bean
    MinioClient presignMinioClient(MinioProperties properties,
            @Value("${app.minio.region:us-east-1}") String region) {
        return MinioClient.builder()
                .endpoint(properties.publicEndpoint())
                // Without a region the SDK calls GetBucketLocation on the browser endpoint.
                .region(region)
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }
}
