package com.fashionstore.catalog.dto;

import com.fashionstore.catalog.model.enumeration.MediaVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PresignUploadRequest {

    @NotBlank
    String filename;

    @NotBlank
    String contentType;

    @NotNull
    @Positive
    Long sizeBytes;

    String displayName;
    String altText;
    String folder;
    List<String> tags;
    MediaVisibility visibility;
}
