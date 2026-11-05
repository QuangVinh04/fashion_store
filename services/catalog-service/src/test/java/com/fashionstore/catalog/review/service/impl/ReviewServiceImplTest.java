package com.fashionstore.catalog.review.service.impl;

import com.fashionstore.catalog.media.api.ReviewImage;
import com.fashionstore.catalog.media.api.ReviewImages;
import com.fashionstore.catalog.product.api.ProductReviewTarget;
import com.fashionstore.catalog.review.client.IdentityUserClient;
import com.fashionstore.catalog.review.client.OrderClient;
import com.fashionstore.catalog.review.dto.CreateReviewRequest;
import com.fashionstore.catalog.review.dto.ProductReviewSummaryResponse;
import com.fashionstore.catalog.review.dto.ReviewResponse;
import com.fashionstore.catalog.review.dto.UpdateReviewRequest;
import com.fashionstore.catalog.review.entity.FitRating;
import com.fashionstore.catalog.review.entity.Review;
import com.fashionstore.catalog.review.entity.ReviewMedia;
import com.fashionstore.catalog.review.entity.ReviewStatus;
import com.fashionstore.catalog.review.exception.ReviewErrorCode;
import com.fashionstore.catalog.review.mapper.ReviewMapper;
import com.fashionstore.catalog.review.repository.ReviewRepository;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.contracts.order.dto.VerifyPurchaseRequest;
import com.fashionstore.contracts.order.dto.VerifyPurchaseResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewServiceImplTest {

    @Mock
    ReviewRepository reviewRepository;
    @Mock
    ProductReviewTarget productReviewTarget;
    @Mock
    ReviewImages reviewImages;
    @Mock
    OrderClient orderClient;
    @Mock
    CurrentUserProvider currentUserProvider;
    @Mock
    IdentityUserClient identityUserClient;
    @Mock
    ProductRatingSync productRatingSync;

    ReviewServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReviewServiceImpl(reviewRepository, productReviewTarget, reviewImages, orderClient,
                currentUserProvider, identityUserClient, productRatingSync, new ReviewMapper());
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
        when(productReviewTarget.variantIds("prod-1")).thenReturn(List.of("var-1"));
        when(productReviewTarget.namesByIds(Set.of("prod-1"))).thenReturn(Map.of("prod-1", "Áo Polo"));
        when(reviewRepository.saveAndFlush(any(Review.class))).thenAnswer(inv -> withId(inv.getArgument(0), "rev-1"));
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createReview_storesReviewWithOwnedImagesReviewerNameAndRefreshesRating() {
        when(orderClient.verifyPurchase(any())).thenReturn(new VerifyPurchaseResponse(true, "order-100"));
        when(reviewImages.requireOwned("user-1", List.of("m-1")))
                .thenReturn(List.of(new ReviewImage("m-1", "http://cdn/api/v1/files/m-1/content")));
        when(identityUserClient.getUserById("user-1")).thenReturn(Optional.of(
                new IdentityUserClient.UserInfoResponse("user-1", "a@example.com", "Nguyễn Văn A", "0901")));
        CreateReviewRequest request = CreateReviewRequest.builder()
                .rating(5).comment("  Vải mát  ").fitRating(FitRating.TRUE_TO_SIZE)
                .customerHeight(170).customerWeight(65).mediaIds(List.of("m-1")).build();

        ReviewResponse response = service.createReview("prod-1", request);

        ArgumentCaptor<Review> saved = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getProductId()).isEqualTo("prod-1");
        assertThat(saved.getValue().getOrderId()).isEqualTo("order-100");
        assertThat(saved.getValue().getReviewerName()).isEqualTo("Nguyễn Văn A");
        assertThat(saved.getValue().getComment()).isEqualTo("Vải mát");
        assertThat(saved.getValue().getMediaList()).extracting(ReviewMedia::getMediaId).containsExactly("m-1");
        verify(productRatingSync).refresh("prod-1");
        assertThat(response.getId()).isEqualTo("rev-1");
        assertThat(response.getProductName()).isEqualTo("Áo Polo");
        assertThat(response.getMediaUrls()).containsExactly("http://cdn/api/v1/files/m-1/content");
    }

    @Test
    void createReview_asksOrderServiceWithTheProductVariants() {
        when(orderClient.verifyPurchase(any())).thenReturn(new VerifyPurchaseResponse(true, "order-100"));

        service.createReview("prod-1", CreateReviewRequest.builder().orderId("order-100").rating(4).build());

        verify(orderClient).verifyPurchase(new VerifyPurchaseRequest("user-1", "order-100", List.of("var-1")));
    }

    @Test
    void createReview_whenProductMissing_stopsBeforeAskingOrderService() {
        org.mockito.Mockito.doThrow(new AppException(ErrorCode.RESOURCE_NOT_FOUND))
                .when(productReviewTarget).requireExists("missing");

        assertThatThrownBy(() -> service.createReview("missing", CreateReviewRequest.builder().rating(5).build()))
                .isInstanceOf(AppException.class);
        verify(orderClient, never()).verifyPurchase(any());
    }

    @Test
    void createReview_whenNotPurchased_throwsNotEligible() {
        when(orderClient.verifyPurchase(any())).thenReturn(new VerifyPurchaseResponse(false, null));

        assertThatThrownBy(() -> service.createReview("prod-1", CreateReviewRequest.builder().rating(5).build()))
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ReviewErrorCode.REVIEW_NOT_ELIGIBLE);
    }

    @Test
    void createReview_whenAlreadyReviewed_throwsAlreadyExists() {
        when(orderClient.verifyPurchase(any())).thenReturn(new VerifyPurchaseResponse(true, "order-100"));
        when(reviewRepository.existsByProductIdAndUserIdAndOrderId("prod-1", "user-1", "order-100")).thenReturn(true);

        assertThatThrownBy(() -> service.createReview("prod-1", CreateReviewRequest.builder().rating(5).build()))
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ReviewErrorCode.REVIEW_ALREADY_EXISTS);
        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void createReview_whenConcurrentDuplicate_throwsAlreadyExistsWithoutRefreshingRating() {
        when(orderClient.verifyPurchase(any())).thenReturn(new VerifyPurchaseResponse(true, "order-100"));
        when(reviewRepository.saveAndFlush(any(Review.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.createReview("prod-1", CreateReviewRequest.builder().rating(5).build()))
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ReviewErrorCode.REVIEW_ALREADY_EXISTS);
        verify(productRatingSync, never()).refresh(any());
    }

    @Test
    void createReview_whenOrderServiceFails_propagatesUpstreamError() {
        when(orderClient.verifyPurchase(any())).thenThrow(new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR));

        assertThatThrownBy(() -> service.createReview("prod-1", CreateReviewRequest.builder().rating(5).build()))
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ErrorCode.UPSTREAM_SERVICE_ERROR);
    }

    @Test
    void createReview_whenIdentityUnavailable_keepsReviewWithoutName() {
        when(orderClient.verifyPurchase(any())).thenReturn(new VerifyPurchaseResponse(true, "order-100"));
        when(identityUserClient.getUserById("user-1")).thenReturn(Optional.empty());

        service.createReview("prod-1", CreateReviewRequest.builder().rating(5).build());

        ArgumentCaptor<Review> saved = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getReviewerName()).isNull();
    }

    @Test
    void updateReview_replacesContentAndImagesThenRefreshesRating() {
        Review review = review("rev-1", "user-1");
        review.getMediaList().add(ReviewMedia.builder().review(review).mediaId("old").url("old-url").build());
        when(reviewRepository.findById("rev-1")).thenReturn(Optional.of(review));
        when(reviewImages.requireOwned("user-1", List.of("m-2")))
                .thenReturn(List.of(new ReviewImage("m-2", "url-2")));

        ReviewResponse response = service.updateReview("prod-1", "rev-1",
                new UpdateReviewRequest(2, "Phai màu", List.of("m-2"), FitRating.RUNS_SMALL, null, null));

        assertThat(review.getRating()).isEqualTo(2);
        assertThat(review.getComment()).isEqualTo("Phai màu");
        assertThat(review.getFitRating()).isEqualTo(FitRating.RUNS_SMALL);
        assertThat(review.getMediaList()).extracting(ReviewMedia::getMediaId).containsExactly("m-2");
        assertThat(response.getMediaUrls()).containsExactly("url-2");
        assertThat(response.getEditedAt()).isNotNull();
        verify(productRatingSync).refresh("prod-1");
    }

    @Test
    void updateReview_whenNotOwner_throwsForbidden() {
        when(reviewRepository.findById("rev-1")).thenReturn(Optional.of(review("rev-1", "user-2")));

        assertThatThrownBy(() -> service.updateReview("prod-1", "rev-1",
                new UpdateReviewRequest(5, null, null, null, null, null)))
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ReviewErrorCode.REVIEW_ACTION_FORBIDDEN);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void updateReview_whenReviewBelongsToAnotherProduct_throwsNotFound() {
        when(reviewRepository.findById("rev-1")).thenReturn(Optional.of(review("rev-1", "user-1")));

        assertThatThrownBy(() -> service.updateReview("prod-2", "rev-1",
                new UpdateReviewRequest(5, null, null, null, null, null)))
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ReviewErrorCode.REVIEW_NOT_FOUND);
    }

    @Test
    void deleteReview_whenOwner_deletesAndRefreshesRating() {
        Review review = review("rev-1", "user-1");
        when(reviewRepository.findById("rev-1")).thenReturn(Optional.of(review));

        service.deleteReview("prod-1", "rev-1");

        verify(reviewRepository).delete(review);
        verify(productRatingSync).refresh("prod-1");
    }

    @Test
    void deleteReview_whenNotOwner_throwsForbiddenEvenForStaff() {
        when(reviewRepository.findById("rev-1")).thenReturn(Optional.of(review("rev-1", "user-2")));

        assertThatThrownBy(() -> service.deleteReview("prod-1", "rev-1"))
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ReviewErrorCode.REVIEW_ACTION_FORBIDDEN);
        verify(reviewRepository, never()).delete(any(Review.class));
    }

    @Test
    void getProductReviews_summarisesVisibleReviewsAndMasksReviewer() {
        Review review = review("rev-1", "user-123456");
        review.setReviewerName("Nguyễn Văn Anh");
        review.setOrderId("order-100");
        PageRequest pageable = PageRequest.of(0, 10);
        when(reviewRepository.findByProductIdAndStatus("prod-1", ReviewStatus.VISIBLE, pageable))
                .thenReturn(new PageImpl<>(List.of(review), pageable, 1));
        when(reviewRepository.getVisibleStats("prod-1")).thenReturn(stats(3, 4.3333));
        when(reviewRepository.countVisibleByRating("prod-1"))
                .thenReturn(List.of(ratingCount(5, 2), ratingCount(3, 1)));

        ProductReviewSummaryResponse summary = service.getProductReviews("prod-1", null, pageable);

        assertThat(summary.getAverageRating()).isEqualTo(4.3);
        assertThat(summary.getTotalReviews()).isEqualTo(3L);
        assertThat(summary.getRatingCounts()).containsEntry(5, 2L).containsEntry(4, 0L).containsEntry(3, 1L);
        ReviewResponse item = summary.getReviews().getItems().get(0);
        assertThat(item.getCustomerName()).isEqualTo("N***h");
        assertThat(item.getUserId()).isEqualTo("u***6");
        assertThat(item.getOrderId()).isNull();
    }

    @Test
    void getProductReviews_filtersByRating() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(reviewRepository.findByProductIdAndRatingAndStatus("prod-1", 5, ReviewStatus.VISIBLE, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));
        when(reviewRepository.getVisibleStats("prod-1")).thenReturn(stats(0, null));

        ProductReviewSummaryResponse summary = service.getProductReviews("prod-1", 5, pageable);

        assertThat(summary.getAverageRating()).isEqualTo(0.0);
        assertThat(summary.getRatingCounts()).hasSize(5);
    }

    @Test
    void getMyReviews_returnsOwnReviewsOfTheOrderWithProductNames() {
        Review review = review("rev-1", "user-1");
        review.setOrderId("order-100");
        review.setStatus(ReviewStatus.HIDDEN);
        PageRequest pageable = PageRequest.of(0, 20);
        when(reviewRepository.findByUserIdAndOrderId("user-1", "order-100", pageable))
                .thenReturn(new PageImpl<>(List.of(review), pageable, 1));

        PageResponse<List<ReviewResponse>> page = service.getMyReviews("order-100", pageable);

        assertThat(page.getTotalElements()).isEqualTo(1L);
        ReviewResponse item = page.getItems().get(0);
        assertThat(item.getProductName()).isEqualTo("Áo Polo");
        assertThat(item.getOrderId()).isEqualTo("order-100");
        assertThat(item.getStatus()).isEqualTo(ReviewStatus.HIDDEN);
    }

    @Test
    void getMyReviews_withoutOrderReturnsAllOwnReviews() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(reviewRepository.findByUserId("user-1", pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        assertThat(service.getMyReviews(null, pageable).getItems()).isEmpty();
    }

    @Test
    void customerActionsRequireLogin() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(null);

        assertThatThrownBy(() -> service.getMyReviews(null, PageRequest.of(0, 20)))
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    static Review review(String id, String userId) {
        Review review = Review.builder().productId("prod-1").userId(userId).rating(5)
                .status(ReviewStatus.VISIBLE).mediaList(new ArrayList<>()).build();
        return withId(review, id);
    }

    static Review withId(Review review, String id) {
        review.setId(id);
        return review;
    }

    static ReviewRepository.RatingStats stats(long total, Double average) {
        return new ReviewRepository.RatingStats() {
            @Override
            public long getTotal() {
                return total;
            }

            @Override
            public Double getAverage() {
                return average;
            }
        };
    }

    static ReviewRepository.RatingCount ratingCount(int rating, long total) {
        return new ReviewRepository.RatingCount() {
            @Override
            public int getRating() {
                return rating;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
