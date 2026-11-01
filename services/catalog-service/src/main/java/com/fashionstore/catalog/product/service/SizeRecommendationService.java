package com.fashionstore.catalog.product.service;

import com.fashionstore.catalog.product.dto.SizeRecommendationRequest;
import com.fashionstore.catalog.product.dto.SizeRecommendationResponse;

/**
 * Suggests which size of a product fits a shopper, using the product's size chart.
 */
public interface SizeRecommendationService {

    SizeRecommendationResponse recommend(String productId, SizeRecommendationRequest request);
}
