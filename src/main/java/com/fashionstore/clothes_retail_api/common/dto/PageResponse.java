package com.fashionstore.clothes_retail_api.common.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageResponse<T> {
    int pageNo;
    int pageSize;
    long totalPage;
    T items;
}
