package com.fashionstore.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductAttributeUpdateRequest {
    @NotBlank(message = "Attribute title is required")
    @Size(max = 255)
    String name; // "Attribute Title"

    @Size(max = 255)
    String displayName;

    Boolean published;
}
