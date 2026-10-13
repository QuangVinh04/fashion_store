package com.fashionstore.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageResponse<T> {

    private int pageNo;
    private int pageSize;
    private long totalPage;
    private T items;
    private Long totalElements;

    /** Keeps the existing four-argument contract for callers that do not supply a count. */
    public PageResponse(int pageNo, int pageSize, long totalPage, T items) {
        this(pageNo, pageSize, totalPage, items, null);
    }
}
