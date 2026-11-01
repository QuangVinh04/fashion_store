package com.fashionstore.catalog.media.mapper;

import com.fashionstore.catalog.media.dto.MediaFileResponse;
import com.fashionstore.catalog.media.entity.MediaFile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.ArrayList;

@Mapper(componentModel = "spring")
public interface MediaFileMapper {

    /** Noi voi GET /api/v1/files/{id}/content de lay ban thu nho; ProductMapper giu ban sao cua chuoi nay. */
    String THUMBNAIL_QUERY = "?variant=thumb";

    @Mapping(target = "url", expression = "java(resolveUrl(file, publicBaseUrl))")
    @Mapping(target = "thumbnailUrl", expression = "java(resolveThumbnailUrl(file, publicBaseUrl))")
    @Mapping(target = "tags", expression = "java(new java.util.ArrayList<>(file.getTags()))")
    MediaFileResponse toResponse(MediaFile file, String publicBaseUrl);

    default String resolveUrl(MediaFile file, String publicBaseUrl) {
        if (file == null || file.getId() == null) {
            return null;
        }
        String baseUrl = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
        return baseUrl + "/api/v1/files/" + file.getId() + "/content";
    }

    default String resolveThumbnailUrl(MediaFile file, String publicBaseUrl) {
        if (file == null || file.getThumbnailKey() == null) {
            return null;
        }
        return resolveUrl(file, publicBaseUrl) + THUMBNAIL_QUERY;
    }

    default ArrayList<String> mapTags(MediaFile file) {
        return new ArrayList<>(file.getTags());
    }
}
