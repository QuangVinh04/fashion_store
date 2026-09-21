package com.fashionstore.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

/**
 * Exposes measurements and fit-finder ranges for one clothing size.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SizeChartRowResponse {
    String id;
    String sizeCode;
    BigDecimal chest;
    BigDecimal waist;
    BigDecimal hip;
    BigDecimal shoulder;
    BigDecimal length;
    BigDecimal inseam;
    BigDecimal heightMin;
    BigDecimal heightMax;
    BigDecimal weightMin;
    BigDecimal weightMax;
}
