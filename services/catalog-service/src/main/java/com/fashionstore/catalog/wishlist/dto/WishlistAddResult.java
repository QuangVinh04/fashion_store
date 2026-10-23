package com.fashionstore.catalog.wishlist.dto;

public record WishlistAddResult(
        WishlistItemResponse item,
        boolean created
) {
}
