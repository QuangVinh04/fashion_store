package com.fashionstore.catalog.service.impl;

import com.fashionstore.catalog.config.MinioProperties;
import com.fashionstore.catalog.dto.PresignedUpload;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Khong can MinIO that: client noi bo (stat/remove/bucket) duoc mock, con client ky
 * presigned URL la client that nhung da set san region nen {@code getRegionAsync}
 * khong goi GetBucketLocation.
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

    private MinioClient internalClient;
    private MinioStorageService storageService;

    @BeforeEach
    void setUp() throws Exception {
        internalClient = mock(MinioClient.class);
        when(internalClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        storageService = new MinioStorageService(internalClient, presignClient(), PROPERTIES);
    }

    private MinioClient presignClient() {
        return MinioClient.builder()
                .endpoint(PROPERTIES.publicEndpoint())
                .region("us-east-1")
                .credentials(PROPERTIES.accessKey(), PROPERTIES.secretKey())
                .build();
    }

    @Test
    void presignUploadCreatesBucketOnceWhenMissing() throws Exception {
        when(internalClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false, true);

        storageService.presignUpload("2026/09/a.png", "image/png");
        storageService.presignUpload("2026/09/b.png", "image/png");

        // bucket tao lan dau can den, va chi mot lan — khong lam lai moi presign
        verify(internalClient, times(1)).makeBucket(any(MakeBucketArgs.class));
        verify(internalClient, times(1)).bucketExists(any(BucketExistsArgs.class));
    }

    @Test
    void presignUploadSkipsBucketCreationWhenAlreadyThere() throws Exception {
        storageService.presignUpload("2026/09/a.png", "image/png");

        verify(internalClient, never()).makeBucket(any(MakeBucketArgs.class));
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
