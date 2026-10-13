package com.fashionstore.common.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {
    @Test void legacyConstructorRemainsCompatibleWithoutInventingTotals() throws Exception {
        var legacy = new PageResponse<>(0, 20, 1, List.of("one"));
        assertThat(legacy.getTotalElements()).isNull();
        assertThat(new ObjectMapper().writeValueAsString(legacy)).doesNotContain("totalElements");
    }

    @Test void serializesExactTotalWhenSupplied() throws Exception {
        var page = PageResponse.builder().pageNo(2).pageSize(20).totalPage(3).items(List.of("one"))
                .totalElements(41L).build();
        assertThat(new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(page)).get("totalElements").asLong()).isEqualTo(41);
    }
}
