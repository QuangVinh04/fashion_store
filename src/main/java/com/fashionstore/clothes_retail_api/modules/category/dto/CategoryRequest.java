package com.fashionstore.clothes_retail_api.modules.category.dto;


import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CategoryRequest {

    @NotBlank(message = "Category name not blank")
    String name;

    String description;

    String parentId;
}
