package com.fashionstore.catalog.dto;

import com.fashionstore.catalog.model.enumeration.MediaVisibility;
import jakarta.validation.constraints.Size;
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
public class MediaFileUpdateRequest {

    @Size(max = 255)
    String displayName;

    @Size(max = 500)
    String altText;

    @Size(max = 255)
    String folder;

    List<@Size(max = 80) String> tags;

    MediaVisibility visibility;
}
