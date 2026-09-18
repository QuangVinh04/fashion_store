package com.fashionstore.catalog.dto.wishlist;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WishlistItemResponse {

    String productId;
    String name;
    String slug;
    BigDecimal basePrice;
    BigDecimal salePrice;
    String thumbnailUrl;
    String brandName;
    String status;
    LocalDateTime addedAt;
}
