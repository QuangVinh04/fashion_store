package com.fashionstore.catalog.media.mapper;

import com.fashionstore.catalog.media.entity.MediaFile;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class MediaFileMapperTest {

    private final MediaFileMapper mapper = Mappers.getMapper(MediaFileMapper.class);

    @Test
    void exposesAThumbnailUrlOnlyWhenAThumbnailWasGenerated() {
        MediaFile withThumbnail = MediaFile.builder().thumbnailKey("a.jpg.thumb.jpg").build();
        withThumbnail.setId("file-1");
        MediaFile withoutThumbnail = MediaFile.builder().build();
        withoutThumbnail.setId("file-2");

        assertThat(mapper.toResponse(withThumbnail, "http://localhost:8087").getThumbnailUrl())
                .isEqualTo("http://localhost:8087/api/v1/files/file-1/content?variant=thumb");
        assertThat(mapper.toResponse(withoutThumbnail, "http://localhost:8087").getThumbnailUrl()).isNull();
    }
}
