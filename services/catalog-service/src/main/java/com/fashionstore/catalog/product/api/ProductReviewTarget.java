package com.fashionstore.catalog.product.api;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** What the review module needs from a product: existence, variants for purchase checks, names and the rating stats. */
public interface ProductReviewTarget {

    /** Throws PRODUCT_NOT_FOUND when the product does not exist. */
    void requireExists(String productId);

    List<String> variantIds(String productId);

    Map<String, String> namesByIds(Collection<String> productIds);

    List<String> idsByNameContaining(String keyword);

    /** Stores the precomputed stats without bumping the product version, so an admin edit in progress is not rejected. */
    void updateRating(String productId, BigDecimal average, int count);
}
