package com.fashionstore.product.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductAttributeOptionResponse {
    String id;
    String attributeId;
    String code;
    String value;
    Boolean published;
}
