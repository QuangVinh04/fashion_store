package com.fashionstore.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fashionstore.catalog.model.enumeration.MediaStatus;
import com.fashionstore.catalog.model.enumeration.MediaType;
import com.fashionstore.catalog.model.enumeration.MediaVisibility;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MediaFileResponse {
    String id;
    String originalFilename;
    String displayName;
    String url;
    String contentType;
    String extension;
    Long sizeBytes;
    String checksumSha256;
    MediaType mediaType;
    MediaStatus status;
    MediaVisibility visibility;
    String altText;
    String folder;
    Integer width;
    Integer height;
    List<String> tags;
    LocalDateTime trashedAt;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
