package com.fashionstore.catalog.service.impl;

import com.fashionstore.catalog.config.MinioProperties;
import com.fashionstore.catalog.dto.PresignedUpload;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chi kiem tra chu ky cua presigned URL nen khong can MinIO that: region duoc set san
 * de {@code getRegionAsync} khong goi GetBucketLocation.
 */
class MinioStorageServiceTest {

    private static final MinioProperties PROPERTIES = new MinioProperties(
            "http://minio:9000",
            "http://localhost:9000",
            "minioadmin",
            "minioadmin",
            "fashion-media",
            900,
            20L * 1024 * 1024);

    private MinioStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new MinioStorageService(client(PROPERTIES.endpoint()),
                client(PROPERTIES.publicEndpoint()), PROPERTIES);
    }

    private MinioClient client(String endpoint) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .region("us-east-1")
                .credentials(PROPERTIES.accessKey(), PROPERTIES.secretKey())
                .build();
    }

    @Test
    void presignUploadSignsContentTypeSoUrlCannotUploadOtherTypes() {
        PresignedUpload upload = storageService.presignUpload("2026/09/object.png", "image/png");

        assertThat(upload.url())
                .startsWith("http://localhost:9000/fashion-media/2026/09/object.png?")
                .contains("X-Amz-SignedHeaders=content-type%3Bhost");
        assertThat(upload.expiresInSeconds()).isEqualTo(900);
    }

    @Test
    void presignUploadSignatureChangesWithContentType() {
        String png = storageService.presignUpload("2026/09/object.png", "image/png").url();
        String html = storageService.presignUpload("2026/09/object.png", "text/html").url();

        assertThat(signature(png)).isNotEqualTo(signature(html));
    }

    @Test
    void presignDownloadDoesNotSignContentType() {
        assertThat(storageService.presignDownload("2026/09/object.png"))
                .contains("X-Amz-SignedHeaders=host")
                .doesNotContain("content-type");
    }

    private String signature(String url) {
        return url.replaceAll("^.*X-Amz-Signature=", "");
    }
}
