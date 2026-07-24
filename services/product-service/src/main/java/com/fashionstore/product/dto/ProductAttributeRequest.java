package com.fashionstore.product.dto;

import com.fashionstore.product.model.enumeration.AttributeType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class ProductAttributeRequest {
    @NotBlank(message = "Attribute name is required")
    String name;
    @NotBlank(message = "Attribute code is required")
    String code;
    @NotNull(message = "Attribute type is required")
    AttributeType type;
    Boolean filterable;
    Boolean searchable;
    @Min(value = 0, message = "Display order must not be negative")
    Integer displayOrder;
}
