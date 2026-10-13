package com.fashionstore.catalog.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
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
    @Size(max = 255) String slug;
    Boolean active;
    @Min(0) Integer displayOrder;

    public CategoryRequest(String name, String description, String parentId) {
        this.name = name;
        this.description = description;
        this.parentId = parentId;
    }
}

