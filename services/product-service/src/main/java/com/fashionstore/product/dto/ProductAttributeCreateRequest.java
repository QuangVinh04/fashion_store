package com.fashionstore.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductAttributeCreateRequest {
    @NotBlank(message = "Attribute title is required")
    @Size(max = 255)
    String name; // "Attribute Title"

    @Size(max = 255)
    String displayName;

    // "Press enter to add variant" — batch value lúc tạo, chỉ cần value thô
    // displayName/published của từng value sẽ điền sau qua endpoint riêng
    List<String> variants;

    Boolean published;
}
