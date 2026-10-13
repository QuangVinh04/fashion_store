package com.fashionstore.catalog.service.impl;

import com.fashionstore.catalog.config.MinioProperties;
import com.fashionstore.catalog.dto.PresignedUpload;
import com.fashionstore.catalog.dto.StoredObject;
import com.fashionstore.catalog.exception.FileErrorCode;
import com.fashionstore.catalog.service.StorageService;
import com.fashionstore.common.exception.AppException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class MinioStorageService implements StorageService {

    private final MinioClient minioClient;
    private final MinioClient presignMinioClient;
    private final MinioProperties properties;
    private volatile boolean bucketReady;
    private volatile boolean evidenceBucketReady;

    public MinioStorageService(MinioClient minioClient,
                               @Qualifier("presignMinioClient") MinioClient presignMinioClient,
                               MinioProperties properties) {
        this.minioClient = minioClient;
        this.presignMinioClient = presignMinioClient;
        this.properties = properties;
    }

    private String bucketFor(String key) {
        return key.startsWith("evidence/") ? properties.evidenceBucket() : properties.bucket();
    }

    private void ensureBucketFor(String key) {
        if (!key.startsWith("evidence/")) return;
        if (evidenceBucketReady) return;
        synchronized (this) {
            if (evidenceBucketReady) return;
            try {
                String bucket = properties.evidenceBucket();
                if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()))
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                try {
                    String policy = minioClient.getBucketPolicy(io.minio.GetBucketPolicyArgs.builder().bucket(bucket).build());
                    if (policy==null) throw new IllegalStateException("Evidence bucket policy unavailable");
                    // MinIO SDK 8.5 returns an empty string for NoSuchBucketPolicy (private by default).
                    var statements = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                            policy.isBlank()?"{\"Statement\":[]}":policy).path("Statement");
                    if (!statements.isArray()) throw new IllegalStateException("Invalid evidence bucket policy");
                    for (var statement : statements) {
                        if ("Allow".equals(statement.path("Effect").asText()))
                            throw new IllegalStateException("Evidence bucket cannot contain an Allow bucket policy");
                    }
                } catch (ErrorResponseException exception) {
                    if (!"NoSuchBucketPolicy".equals(exception.errorResponse().code())) throw exception;
                }
                evidenceBucketReady = true;
            } catch (Exception exception) { throw new AppException(FileErrorCode.FILE_STORAGE_FAILED, exception); }
        }
    }

    /**
     * Bucket duoc tao lan dau can den chu khong phai luc khoi dong: MinIO chua san sang
     * thi service van boot, va van tu tao bucket khi MinIO len sau. Kiem tra o
     * {@code @PostConstruct} lam startup phu thuoc vao MinIO ma van khong tu chua duoc
     * neu MinIO len muon.
     */
    private void ensureBucket() {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            try {
                boolean exists = minioClient.bucketExists(
                        BucketExistsArgs.builder().bucket(properties.bucket()).build());
                if (!exists) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
                    log.info("[Media] created bucket {}", properties.bucket());
                }
                bucketReady = true;
            } catch (Exception exception) {
                throw new AppException(FileErrorCode.FILE_STORAGE_FAILED, exception);
            }
        }
    }

    /**
     * {@code Content-Type} nam trong {@code extraHeaders} nen no vao ca canonical request va
     * {@code X-Amz-SignedHeaders}: URL chi upload duoc dung content type da khai, PUT bang
     * type khac se bi MinIO tra {@code SignatureDoesNotMatch}. Khong ky header nay thi
     * allow-list o buoc presign chi la loi de nghi, client PUT gi cung duoc.
     */
    @Override
    public PresignedUpload presignUpload(String storageKey, String contentType) {
        return presignUpload(storageKey, contentType, Map.of());
    }

    @Override
    public PresignedUpload presignUpload(String storageKey, String contentType, Map<String, String> requiredHeaders) {
        if (storageKey.startsWith("evidence/")) ensureBucketFor(storageKey); else ensureBucket();
        try {
            Map<String, String> headers = new HashMap<>(requiredHeaders);
            headers.put("Content-Type", contentType);
            String url = presignMinioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(bucketFor(storageKey))
                    .object(storageKey)
                    .extraHeaders(headers)
                    .expiry(properties.presignExpirySeconds(), TimeUnit.SECONDS)
                    .build());
            return new PresignedUpload(url, properties.presignExpirySeconds());
        } catch (Exception exception) {
            throw new AppException(FileErrorCode.FILE_STORAGE_FAILED, exception);
        }
    }

    @Override
    public String presignDownload(String storageKey) {
        ensureBucketFor(storageKey);
        try {
            return presignMinioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketFor(storageKey))
                    .object(storageKey)
                    .expiry(properties.presignExpirySeconds(), TimeUnit.SECONDS)
                    .build());
        } catch (Exception exception) {
            throw new AppException(FileErrorCode.FILE_STORAGE_FAILED, exception);
        }
    }

    @Override
    public StoredObject stat(String storageKey) {
        ensureBucketFor(storageKey);
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketFor(storageKey))
                    .object(storageKey)
                    .build());
            return new StoredObject(stat.size(), stat.contentType(), stat.etag());
        } catch (ErrorResponseException exception) {
            if ("NoSuchKey".equals(exception.errorResponse().code())) {
                return null;
            }
            throw new AppException(FileErrorCode.FILE_STORAGE_FAILED, exception);
        } catch (Exception exception) {
            throw new AppException(FileErrorCode.FILE_STORAGE_FAILED, exception);
        }
    }

    @Override
    public byte[] readPrefix(String storageKey, int maxBytes) {
        ensureBucketFor(storageKey);
        try (InputStream is = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketFor(storageKey))
                .object(storageKey)
                .offset(0L)
                .length((long) maxBytes)
                .build())) {
            return is.readNBytes(maxBytes);
        } catch (ErrorResponseException exception) {
            if ("NoSuchKey".equals(exception.errorResponse().code())) {
                return null;
            }
            throw new AppException(FileErrorCode.FILE_STORAGE_FAILED, exception);
        } catch (Exception exception) {
            throw new AppException(FileErrorCode.FILE_STORAGE_FAILED, exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        ensureBucketFor(storageKey);
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketFor(storageKey))
                    .object(storageKey)
                    .build());
        } catch (Exception exception) {
            throw new AppException(FileErrorCode.FILE_STORAGE_FAILED, exception);
        }
    }
}
