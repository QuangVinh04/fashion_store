package com.fashionstore.clothes_retail_api.common.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL) // giá trị nào = null thì ko trả về
public class PageResponse<T> {
    int pageNo;
    int pageSize;
    long totalPage;
    T items;
}
