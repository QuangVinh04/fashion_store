package com.fashionstore.clothes_retail_api.modules.product.dto;



import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductRequest {
    @NotBlank(message = "Product name is required")
    @Size(max = 200, message = "Product name must not exceed 200 characters")
    String name;

    String description;

    @NotNull(message = "Base price is required")
    @Min(value = 0, message = "Base price cannot be negative")
    BigDecimal price;

    @NotNull(message = "Category ID is required")
    String categoryId;
}
