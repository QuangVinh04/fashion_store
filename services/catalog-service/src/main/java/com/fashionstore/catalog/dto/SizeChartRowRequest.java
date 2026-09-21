package com.fashionstore.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

/**
 * Defines measurements and fit-finder ranges for a size-chart row.
 */
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

    @DecimalMin(value = "0", inclusive = false, message = "Minimum height must be greater than zero")
    BigDecimal heightMin;

    @DecimalMin(value = "0", inclusive = false, message = "Maximum height must be greater than zero")
    BigDecimal heightMax;

    @DecimalMin(value = "0", inclusive = false, message = "Minimum weight must be greater than zero")
    BigDecimal weightMin;

    @DecimalMin(value = "0", inclusive = false, message = "Maximum weight must be greater than zero")
    BigDecimal weightMax;
}
