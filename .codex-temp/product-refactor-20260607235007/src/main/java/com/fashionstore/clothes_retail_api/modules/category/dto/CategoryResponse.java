package com.fashionstore.clothes_retail_api.modules.category.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CategoryResponse {
    String id;

    String name;

    String slug;

    String description;

    String parentId;

    List<CategoryResponse> children;

    LocalDateTime createdAt;

    LocalDateTime updatedAt;
}
