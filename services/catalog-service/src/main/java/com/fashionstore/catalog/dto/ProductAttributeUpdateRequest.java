package com.fashionstore.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import com.fashionstore.catalog.entity.enumeration.AttributeType;
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
    @Size(max = 50)
    String name; // "Attribute Title"

    @Size(max = 255)
    String displayName;

    Boolean published;
    AttributeType type;
    Boolean filterable;
    Boolean searchable;
    @Min(0) Integer displayOrder;
}
