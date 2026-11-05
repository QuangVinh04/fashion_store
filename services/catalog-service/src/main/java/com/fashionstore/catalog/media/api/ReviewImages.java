package com.fashionstore.catalog.media.api;

import java.util.List;

public interface ReviewImages {

    /**
     * Ảnh đánh giá theo đúng thứ tự {@code mediaIds}. Từ chối nếu ảnh không tồn tại, không thuộc {@code ownerId},
     * không phải {@code REVIEW_IMAGE} hoặc chưa upload xong.
     */
    List<ReviewImage> requireOwned(String ownerId, List<String> mediaIds);
}
