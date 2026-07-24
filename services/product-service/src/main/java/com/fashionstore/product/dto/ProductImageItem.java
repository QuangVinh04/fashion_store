package com.fashionstore.product.dto;


import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImageItem {
    String mediaId;
    String url;
    String altText;
    Integer sortOrder;
    Boolean isPrimary;
}
