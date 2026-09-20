package com.fashionstore.catalog.dto;


import lombok.*;

/** Input model for a product gallery image, optionally scoped to a canonical color option. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImageItem {
    String mediaId;
    String url;
    String colorOptionId;
    String color;
    String altText;
    Integer sortOrder;
    Boolean isPrimary;
}
