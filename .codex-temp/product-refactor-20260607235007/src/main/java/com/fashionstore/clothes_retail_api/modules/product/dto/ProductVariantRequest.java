package com.fashionstore.clothes_retail_api.modules.product.dto;


import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductVariantRequest {
    @NotBlank(message = "Size is required")
    String size;

    @NotBlank(message = "Color is required")
    String color;

    @NotBlank(message = "SKU is required")
    String sku;

    @NotNull(message = "Variant price is required")
    @Min(value = 0, message = "Price cannot be negative")
    BigDecimal price;

    @NotNull(message = "Stock quantity is required")
    @Min(value = 0, message = "Stock cannot be negative")
    Integer stock;
}
