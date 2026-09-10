package com.fashionstore.catalog.service.impl;

import com.fashionstore.catalog.config.MinioProperties;
import com.fashionstore.catalog.dto.PresignedUpload;
import com.fashionstore.catalog.dto.StoredObject;
import com.fashionstore.catalog.exception.FileErrorCode;
import com.fashionstore.catalog.service.StorageService;
import com.fashionstore.common.exception.AppException;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class MinioStorageService implements StorageService {

    private final MinioClient minioClient;
    private final MinioClient presignMinioClient;
    private final MinioProperties properties;

    public MinioStorageService(MinioClient minioClient,
                               @Qualifier("presignMinioClient") MinioClient presignMinioClient,
                               MinioProperties properties) {
        this.minioClient = minioClient;
        this.presignMinioClient = presignMinioClient;
        this.properties = properties;
    }

    @PostConstruct
    void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(properties.bucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
                log.info("[Media] created bucket {}", properties.bucket());
            }
        } catch (Exception exception) {
            throw new AppException(FileErrorCode.FILE_STORAGE_FAILED, exception);
        }
    }

    @Override
    public PresignedUpload presignUpload(String storageKey, String contentType) {
        try {
            String url = presignMinioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(properties.bucket())
                    .object(storageKey)
                    .expiry(properties.presignExpirySeconds(), TimeUnit.SECONDS)
                    .build());
            return new PresignedUpload(url, properties.presignExpirySeconds());
        } catch (Exception exception) {
            throw new AppException(FileErrorCode.FILE_STORAGE_FAILED, exception);
        }
    }

    @Override
    public String presignDownload(String storageKey) {
        try {
            return presignMinioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.bucket())
                    .object(storageKey)
                    .expiry(properties.presignExpirySeconds(), TimeUnit.SECONDS)
                    .build());
        } catch (Exception exception) {
            throw new AppException(FileErrorCode.FILE_STORAGE_FAILED, exception);
        }
    }

    @Override
    public StoredObject stat(String storageKey) {
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(properties.bucket())
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
    public void delete(String storageKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(storageKey)
                    .build());
        } catch (Exception exception) {
            throw new AppException(FileErrorCode.FILE_STORAGE_FAILED, exception);
        }
    }
}
