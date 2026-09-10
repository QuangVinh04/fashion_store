package com.fashionstore.catalog.dto;

import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductAttributeOptionRequest {
    @Size(max = 255)
    String name;

    @Size(max = 255)
    String value;

    @Size(max = 255)
    String displayName;

    Boolean published;
}
