package com.fashionstore.catalog.review.mapper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewMapperTest {

    @Test
    void masksReviewerNameKeepingFirstAndLastLetter() {
        assertThat(ReviewMapper.maskName("  Nguyễn Văn Anh ")).isEqualTo("N***h");
        assertThat(ReviewMapper.maskName("A")).isEqualTo("A***");
    }

    @Test
    void reviewWithoutNameShowsGenericLabel() {
        assertThat(ReviewMapper.maskName(null)).isEqualTo(ReviewMapper.ANONYMOUS_REVIEWER);
        assertThat(ReviewMapper.maskName("   ")).isEqualTo(ReviewMapper.ANONYMOUS_REVIEWER);
    }
}
