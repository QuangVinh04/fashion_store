package com.fashionstore.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductAttributeValueRequest {

    @NotBlank(message = "Attribute id is required")
    String attributeId;

    @NotBlank(message = "Attribute value is required")
    @Size(max = 255)
    String value;

    @Size(max = 255)
    String normalizedValue;
}
