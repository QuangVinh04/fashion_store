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
public class ProductAttributeValueResponse {

    String id;
    String attributeId;
    String code;
    String name;
    String value;
    List<String> values;
    String displayName;
    Boolean published;
    Integer position;
}
