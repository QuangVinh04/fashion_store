package com.fashionstore.catalog.product.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductAttributeOptionRequest {
    @NotBlank
    @Size(max = 255)
    String name;

    @Size(max = 255)
    String value;

    @Size(max = 255)
    String displayName;

    Boolean published;
}
