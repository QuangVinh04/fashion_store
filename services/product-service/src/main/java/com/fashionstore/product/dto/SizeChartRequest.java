package com.fashionstore.product.dto;

import com.fashionstore.product.model.enumeration.Gender;
import com.fashionstore.product.model.enumeration.ProductType;
import jakarta.validation.constraints.NotBlank;
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
public class SizeChartRequest {
    @NotBlank(message = "Size chart name is required")
    String name;
    @NotBlank(message = "Unit is required")
    String unit;
    Gender gender;
    ProductType productType;
    Boolean active;
}
