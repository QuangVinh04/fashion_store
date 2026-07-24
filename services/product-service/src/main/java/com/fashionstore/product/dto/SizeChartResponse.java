package com.fashionstore.product.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fashionstore.product.model.enumeration.Gender;
import com.fashionstore.product.model.enumeration.ProductType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SizeChartResponse {
    String id;
    String name;
    String unit;
    Gender gender;
    ProductType productType;
    Boolean active;
    List<SizeChartRowResponse> rows;
}
