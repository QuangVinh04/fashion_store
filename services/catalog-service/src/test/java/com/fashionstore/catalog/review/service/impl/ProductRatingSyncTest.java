package com.fashionstore.catalog.review.service.impl;

import com.fashionstore.catalog.product.api.ProductReviewTarget;
import com.fashionstore.catalog.review.repository.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductRatingSyncTest {

    @Mock
    ReviewRepository reviewRepository;
    @Mock
    ProductReviewTarget productReviewTarget;
    @InjectMocks
    ProductRatingSync productRatingSync;

    @Test
    void locksBeforeReadingStatsThenStoresRoundedAverage() {
        when(reviewRepository.getVisibleStats("prod-1")).thenReturn(ReviewServiceImplTest.stats(3, 4.35));

        productRatingSync.refresh("prod-1");

        InOrder order = inOrder(reviewRepository, productReviewTarget);
        order.verify(reviewRepository).lockProductRating("prod-1");
        order.verify(reviewRepository).getVisibleStats("prod-1");
        order.verify(productReviewTarget).updateRating("prod-1", new BigDecimal("4.4"), 3);
    }

    @Test
    void storesZeroWhenNoVisibleReviewLeft() {
        when(reviewRepository.getVisibleStats("prod-1")).thenReturn(ReviewServiceImplTest.stats(0, null));

        productRatingSync.refresh("prod-1");

        verify(productReviewTarget).updateRating("prod-1", new BigDecimal("0.0"), 0);
    }
}
