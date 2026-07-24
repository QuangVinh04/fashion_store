package com.fashionstore.product.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
}
