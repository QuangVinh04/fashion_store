package com.fashionstore.catalog.dto;

import com.fashionstore.catalog.entity.enumeration.Gender;
import com.fashionstore.catalog.entity.enumeration.ProductType;
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
public class SizeChartRequest {
    @NotBlank(message = "Size chart name is required")
    @Size(max = 255)
    String name;
    @NotBlank(message = "Unit is required")
    @Size(max = 20)
    String unit;
    Gender gender;
    ProductType productType;
    Boolean active;
}
