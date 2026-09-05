package com.fashionstore.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fashionstore.catalog.model.enumeration.AttributeType;
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
public class ProductAttributeResponse {

    String id;
    String name;
    String code;
    AttributeType type;
    String displayName;
    Boolean filterable;
    Boolean searchable;
    Integer displayOrder;
    Boolean published;
    List<ProductAttributeValueResponse> values;
}
