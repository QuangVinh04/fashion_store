package com.fashionstore.product.dto.option;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class SizeOptionResponse {
    String id;
    String name;
    Integer displayOrder;
    Boolean active;
}