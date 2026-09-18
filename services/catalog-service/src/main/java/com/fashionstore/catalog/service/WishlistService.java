package com.fashionstore.catalog.service;

import com.fashionstore.catalog.dto.wishlist.WishlistAddResult;
import com.fashionstore.catalog.dto.wishlist.WishlistCheckResponse;
import com.fashionstore.catalog.dto.wishlist.WishlistItemResponse;
import com.fashionstore.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface WishlistService {

    WishlistAddResult addToWishlist(String productId);

    void removeFromWishlist(String productId);

    PageResponse<List<WishlistItemResponse>> getMyWishlist(Pageable pageable);

    WishlistCheckResponse checkInWishlist(String productId);
}
