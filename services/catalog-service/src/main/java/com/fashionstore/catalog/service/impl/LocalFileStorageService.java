package com.fashionstore.catalog.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.catalog.config.FileStorageProperties;
import com.fashionstore.catalog.dto.StoredFile;
import com.fashionstore.catalog.exception.FileErrorCode;
import com.fashionstore.catalog.service.StorageService;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LocalFileStorageService implements StorageService {

    Path rootLocation;

    public LocalFileStorageService(FileStorageProperties properties) {
        this.rootLocation = Path.of(properties.location()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException exception) {
            throw new AppException(FileErrorCode.FILE_STORAGE_FAILED, exception);
        }
    }

    @Override
    public StoredFile store(byte[] content, String originalFilename) {
        if (content == null || content.length == 0) {
            throw new AppException(FileErrorCode.FILE_UPLOAD_INVALID);
        }

        String extension = resolveExtension(originalFilename);
        String storedFilename = UUID.randomUUID() + (extension == null ? "" : "." + extension);
        LocalDate now = LocalDate.now();
        String storageKey = now.getYear() + "/" + pad(now.getMonthValue()) + "/" + storedFilename;
        Path target = rootLocation.resolve(storageKey).normalize();

        if (!target.startsWith(rootLocation)) {
            throw new AppException(FileErrorCode.FILE_STORAGE_FAILED);
        }

        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content, StandardOpenOption.CREATE_NEW);
            return new StoredFile(storageKey, storedFilename, extension);
        } catch (IOException exception) {
            throw new AppException(FileErrorCode.FILE_STORAGE_FAILED, exception);
        }
    }

    @Override
    public Resource load(String storageKey) {
        try {
            Path path = rootLocation.resolve(storageKey).normalize();
            if (!path.startsWith(rootLocation) || !Files.exists(path)) {
                throw new AppException(FileErrorCode.FILE_NOT_FOUND);
            }
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new AppException(FileErrorCode.FILE_NOT_FOUND);
            }
            return resource;
        } catch (MalformedURLException exception) {
            throw new AppException(FileErrorCode.FILE_STORAGE_FAILED, exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Path path = rootLocation.resolve(storageKey).normalize();
            if (path.startsWith(rootLocation)) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            throw new AppException(FileErrorCode.FILE_STORAGE_FAILED, exception);
        }
    }

    private String resolveExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        return filename.substring(dot + 1)
                .replaceAll("[^A-Za-z0-9]", "")
                .toLowerCase(Locale.ROOT);
    }

    private String pad(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }
}
