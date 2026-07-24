package com.fashionstore.file.mapper;

import com.fashionstore.file.dto.MediaFileResponse;
import com.fashionstore.file.model.MediaFile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.ArrayList;

@Mapper(componentModel = "spring")
public interface MediaFileMapper {

    @Mapping(target = "url", expression = "java(resolveUrl(file, publicBaseUrl))")
    @Mapping(target = "tags", expression = "java(new java.util.ArrayList<>(file.getTags()))")
    MediaFileResponse toResponse(MediaFile file, String publicBaseUrl);

    default String resolveUrl(MediaFile file, String publicBaseUrl) {
        if (file == null || file.getId() == null) {
            return null;
        }
        String baseUrl = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
        return baseUrl + "/api/v1/files/" + file.getId() + "/content";
    }

    default ArrayList<String> mapTags(MediaFile file) {
        return new ArrayList<>(file.getTags());
    }
}
