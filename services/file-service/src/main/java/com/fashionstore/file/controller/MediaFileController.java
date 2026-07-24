package com.fashionstore.file.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.file.dto.MediaFileContent;
import com.fashionstore.file.dto.MediaFileResponse;
import com.fashionstore.file.dto.MediaFileUpdateRequest;
import com.fashionstore.file.model.enumeration.MediaStatus;
import com.fashionstore.file.model.enumeration.MediaType;
import com.fashionstore.file.model.enumeration.MediaVisibility;
import com.fashionstore.file.service.MediaFileService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MediaFileController {

    MediaFileService mediaFileService;

    @PostMapping(consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MediaFileResponse> upload(@RequestPart("file") MultipartFile file,
                                                 @RequestParam(required = false) String displayName,
                                                 @RequestParam(required = false) String altText,
                                                 @RequestParam(required = false) String folder,
                                                 @RequestParam(required = false) List<String> tags,
                                                 @RequestParam(required = false) MediaVisibility visibility) {
        return ApiResponse.<MediaFileResponse>builder()
                .message("Upload file successfully")
                .data(mediaFileService.upload(file, displayName, altText, folder, tags, visibility))
                .build();
    }

    @GetMapping
    public ApiResponse<PageResponse<List<MediaFileResponse>>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) MediaType mediaType,
            @RequestParam(required = false) String folder,
            @RequestParam(required = false, defaultValue = "ACTIVE") MediaStatus status,
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.<PageResponse<List<MediaFileResponse>>>builder()
                .message("Get files successfully")
                .data(mediaFileService.search(pageable, keyword, mediaType, folder, status))
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<MediaFileResponse> getById(@PathVariable String id) {
        return ApiResponse.<MediaFileResponse>builder()
                .message("Get file successfully")
                .data(mediaFileService.getById(id))
                .build();
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> getContent(@PathVariable String id) {
        MediaFileContent content = mediaFileService.loadContent(id);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(content.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(content.filename())
                        .build()
                        .toString())
                .body(content.resource());
    }

    @PatchMapping("/{id}")
    public ApiResponse<MediaFileResponse> update(@PathVariable String id,
                                                 @Valid @RequestBody MediaFileUpdateRequest request) {
        return ApiResponse.<MediaFileResponse>builder()
                .message("Update file successfully")
                .data(mediaFileService.update(id, request))
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> moveToTrash(@PathVariable String id) {
        mediaFileService.moveToTrash(id);
        return ApiResponse.<Void>builder()
                .message("Move file to trash successfully")
                .build();
    }

    @PostMapping("/{id}/restore")
    public ApiResponse<MediaFileResponse> restore(@PathVariable String id) {
        return ApiResponse.<MediaFileResponse>builder()
                .message("Restore file successfully")
                .data(mediaFileService.restore(id))
                .build();
    }

    @DeleteMapping("/{id}/permanent")
    public ApiResponse<Void> deletePermanently(@PathVariable String id) {
        mediaFileService.deletePermanently(id);
        return ApiResponse.<Void>builder()
                .message("Delete file permanently successfully")
                .build();
    }
}
