package com.fashionstore.catalog.dto.wishlist;

public record WishlistAddResult(
        WishlistItemResponse item,
        boolean created
) {
}
