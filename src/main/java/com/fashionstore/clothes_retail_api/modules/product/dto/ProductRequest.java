package com.fashionstore.clothes_retail_api.modules.product.dto;



import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;

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

    @NotEmpty(message = "Product must have at least one variant")
    List<ProductVariantRequest> variants;
}
