package com.fashionstore.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SizeChartRowRequest {
    @NotBlank(message = "Size code is required")
    String sizeCode;
    BigDecimal chest;
    BigDecimal waist;
    BigDecimal hip;
    BigDecimal shoulder;
    BigDecimal length;
    BigDecimal inseam;
}
