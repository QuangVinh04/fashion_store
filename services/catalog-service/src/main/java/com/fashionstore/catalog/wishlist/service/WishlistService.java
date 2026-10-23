package com.fashionstore.catalog.wishlist.service;

import com.fashionstore.catalog.wishlist.dto.WishlistAddResult;
import com.fashionstore.catalog.wishlist.dto.WishlistCheckResponse;
import com.fashionstore.catalog.wishlist.dto.WishlistItemResponse;
import com.fashionstore.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface WishlistService {

    WishlistAddResult addToWishlist(String productId);

    void removeFromWishlist(String productId);

    PageResponse<List<WishlistItemResponse>> getMyWishlist(Pageable pageable);

    WishlistCheckResponse checkInWishlist(String productId);
}
