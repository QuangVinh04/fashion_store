package com.fashionstore.catalog.review.service.impl;

import com.fashionstore.catalog.review.client.IdentityUserClient;
import com.fashionstore.catalog.review.client.OrderClient;
import com.fashionstore.catalog.review.dto.CreateReviewRequest;
import com.fashionstore.catalog.review.dto.ProductReviewSummaryResponse;
import com.fashionstore.catalog.review.dto.ReviewResponse;
import com.fashionstore.catalog.product.entity.Product;
import com.fashionstore.catalog.product.entity.ProductVariant;
import com.fashionstore.catalog.review.entity.Review;
import com.fashionstore.catalog.review.entity.FitRating;
import com.fashionstore.catalog.review.entity.ReviewStatus;
import com.fashionstore.catalog.product.exception.ProductErrorCode;
import com.fashionstore.catalog.product.repository.ProductRepository;
import com.fashionstore.catalog.product.repository.ProductVariantRepository;
import com.fashionstore.catalog.review.repository.ReviewRepository;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.contracts.order.dto.VerifyPurchaseResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewServiceImplTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private OrderClient orderClient;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private IdentityUserClient identityUserClient;

    private ReviewServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReviewServiceImpl(
                reviewRepository,
                productRepository,
                productVariantRepository,
                orderClient,
                currentUserProvider,
                identityUserClient
        );
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
    }

    @Test
    void createReview_whenEligibleAndNotReviewed_createsReviewSuccessfully() {
        Product product = Product.builder().name("Áo Polo").build();
        product.setId("prod-1");

        ProductVariant variant = ProductVariant.builder().sku("POLO-M").build();
        variant.setId("var-1");

        when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));
        when(productVariantRepository.findByProductId("prod-1")).thenReturn(List.of(variant));
        when(orderClient.verifyPurchase(any())).thenReturn(new VerifyPurchaseResponse(true, "order-100"));
        when(reviewRepository.existsByProductIdAndUserIdAndOrderId("prod-1", "user-1", "order-100")).thenReturn(false);
        when(reviewRepository.saveAndFlush(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setId("rev-1");
            return r;
        });

        CreateReviewRequest request = CreateReviewRequest.builder()
                .rating(5)
                .comment("Chất vải thoáng mát, đường may kỹ càng")
                .fitRating(FitRating.TRUE_TO_SIZE)
                .customerHeight(170)
                .customerWeight(65)
                .mediaUrls(List.of("http://example.com/photo1.jpg"))
                .build();

        ReviewResponse response = service.createReview("prod-1", request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("rev-1");
        assertThat(response.getProductId()).isEqualTo("prod-1");
        assertThat(response.getUserId()).isEqualTo("user-1");
        assertThat(response.getOrderId()).isEqualTo("order-100");
        assertThat(response.getRating()).isEqualTo(5);
        assertThat(response.getComment()).isEqualTo("Chất vải thoáng mát, đường may kỹ càng");
        assertThat(response.getVerifiedPurchase()).isTrue();
        assertThat(response.getStatus()).isEqualTo(ReviewStatus.VISIBLE);
        assertThat(response.getFitRating()).isEqualTo(FitRating.TRUE_TO_SIZE);
        assertThat(response.getCustomerHeight()).isEqualTo(170);
        assertThat(response.getCustomerWeight()).isEqualTo(65);
        assertThat(response.getMediaUrls()).containsExactly("http://example.com/photo1.jpg");

        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository, times(1)).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getRating()).isEqualTo(5);
        assertThat(captor.getValue().getVerifiedPurchase()).isTrue();
        assertThat(captor.getValue().getMediaList()).hasSize(1);
    }

    @Test
    void createReview_whenConcurrentRaceCondition_throwsReviewAlreadyExists() {
        Product product = Product.builder().name("Áo Polo").build();
        product.setId("prod-1");

        when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));
        when(productVariantRepository.findByProductId("prod-1")).thenReturn(List.of());
        when(orderClient.verifyPurchase(any())).thenReturn(new VerifyPurchaseResponse(true, "order-100"));
        when(reviewRepository.existsByProductIdAndUserIdAndOrderId("prod-1", "user-1", "order-100")).thenReturn(false);
        when(reviewRepository.saveAndFlush(any(Review.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

        CreateReviewRequest request = CreateReviewRequest.builder().rating(5).build();

        assertThatThrownBy(() -> service.createReview("prod-1", request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ProductErrorCode.REVIEW_ALREADY_EXISTS));
    }

    @Test
    void createReview_whenOrderClientFails_propagatesException() {
        Product product = Product.builder().name("Áo Polo").build();
        product.setId("prod-1");

        when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));
        when(productVariantRepository.findByProductId("prod-1")).thenReturn(List.of());
        when(orderClient.verifyPurchase(any()))
                .thenThrow(new AppException(com.fashionstore.common.exception.ErrorCode.UPSTREAM_SERVICE_ERROR));

        CreateReviewRequest request = CreateReviewRequest.builder().rating(5).build();

        assertThatThrownBy(() -> service.createReview("prod-1", request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(com.fashionstore.common.exception.ErrorCode.UPSTREAM_SERVICE_ERROR));
    }

    @Test
    void createReview_whenNotEligible_throwsReviewNotEligible() {
        Product product = Product.builder().name("Áo Thun").build();
        product.setId("prod-1");

        when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));
        when(productVariantRepository.findByProductId("prod-1")).thenReturn(List.of());
        when(orderClient.verifyPurchase(any())).thenReturn(new VerifyPurchaseResponse(false, null));

        CreateReviewRequest request = CreateReviewRequest.builder().rating(5).build();

        assertThatThrownBy(() -> service.createReview("prod-1", request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ProductErrorCode.REVIEW_NOT_ELIGIBLE));

        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void createReview_whenAlreadyReviewed_throwsReviewAlreadyExists() {
        Product product = Product.builder().name("Quần Jean").build();
        product.setId("prod-1");

        when(productRepository.findById("prod-1")).thenReturn(Optional.of(product));
        when(productVariantRepository.findByProductId("prod-1")).thenReturn(List.of());
        when(orderClient.verifyPurchase(any())).thenReturn(new VerifyPurchaseResponse(true, "order-100"));
        when(reviewRepository.existsByProductIdAndUserIdAndOrderId("prod-1", "user-1", "order-100")).thenReturn(true);

        CreateReviewRequest request = CreateReviewRequest.builder().rating(4).build();

        assertThatThrownBy(() -> service.createReview("prod-1", request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ProductErrorCode.REVIEW_ALREADY_EXISTS));

        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void getProductReviews_computesSummaryAndReturnsPaginated() {
        Product product = Product.builder().name("Váy Nữ").build();
        product.setId("prod-1");

        Review r1 = Review.builder().product(product).userId("user-1").rating(5).comment("Đẹp").status(ReviewStatus.VISIBLE).build();
        r1.setId("rev-1");
        Review r2 = Review.builder().product(product).userId("user-2").rating(4).comment("Khá ổn").status(ReviewStatus.VISIBLE).build();
        r2.setId("rev-2");

        when(productRepository.existsById("prod-1")).thenReturn(true);
        when(reviewRepository.findByProductIdAndStatus(eq("prod-1"), eq(ReviewStatus.VISIBLE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(r1, r2), PageRequest.of(0, 10), 2));
        when(reviewRepository.getAverageRatingByProductId("prod-1")).thenReturn(4.5);
        when(reviewRepository.countByProductIdAndStatus("prod-1", ReviewStatus.VISIBLE)).thenReturn(2L);
        when(reviewRepository.countReviewsGroupByRating("prod-1")).thenReturn(List.of(
                new Object[]{5, 1L},
                new Object[]{4, 1L}
        ));

        ProductReviewSummaryResponse response = service.getProductReviews("prod-1", null, PageRequest.of(0, 10));

        assertThat(response).isNotNull();
        assertThat(response.getAverageRating()).isEqualTo(4.5);
        assertThat(response.getTotalReviews()).isEqualTo(2L);
        assertThat(response.getRatingCounts()).containsEntry(5, 1L);
        assertThat(response.getRatingCounts()).containsEntry(4, 1L);
        assertThat(response.getRatingCounts()).containsEntry(3, 0L);
        assertThat(response.getReviews().getItems()).hasSize(2);
        assertThat(response.getReviews().getItems().get(0).getUserId()).isEqualTo("u***1");
        assertThat(response.getReviews().getItems().get(0).getOrderId()).isNull();
    }

    @Test
    void updateReviewStatus_updatesAndReturnsAdminResponse() {
        Review review = Review.builder().userId("user-123").status(ReviewStatus.VISIBLE).build();
        review.setId("rev-1");

        when(reviewRepository.findById("rev-1")).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewResponse response = service.updateReviewStatus("rev-1", ReviewStatus.HIDDEN);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(ReviewStatus.HIDDEN);
        verify(reviewRepository).save(review);
    }

    @Test
    void replyReview_setsReplyCommentAndStaffId() {
        Review review = Review.builder().userId("user-123").build();
        review.setId("rev-1");

        when(reviewRepository.findById("rev-1")).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewResponse response = service.replyReview("rev-1", "Cảm ơn bạn đã phản hồi");

        assertThat(response).isNotNull();
        assertThat(response.getReplyComment()).isEqualTo("Cảm ơn bạn đã phản hồi");
        assertThat(response.getRepliedBy()).isEqualTo("user-1");
        assertThat(response.getRepliedAt()).isNotNull();
    }

    @Test
    void deleteReply_clearsReplyFields() {
        Review review = Review.builder().userId("user-123").replyComment("Cũ").build();
        review.setId("rev-1");

        when(reviewRepository.findById("rev-1")).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewResponse response = service.deleteReply("rev-1");

        assertThat(response).isNotNull();
        assertThat(response.getReplyComment()).isNull();
        assertThat(response.getRepliedAt()).isNull();
        assertThat(response.getRepliedBy()).isNull();
    }

    @Test
    void deleteReview_whenOwner_deletesSuccessfully() {
        Product product = Product.builder().name("Áo Khoác").build();
        product.setId("prod-1");

        Review review = Review.builder().product(product).userId("user-1").rating(5).build();
        review.setId("rev-1");

        when(reviewRepository.findById("rev-1")).thenReturn(Optional.of(review));

        service.deleteReview("prod-1", "rev-1");

        verify(reviewRepository, times(1)).delete(review);
    }

    @Test
    void deleteReview_whenNotOwnerAndNotAdmin_throwsForbidden() {
        Product product = Product.builder().name("Áo Khoác").build();
        product.setId("prod-1");

        Review review = Review.builder().product(product).userId("user-2").rating(5).build();
        review.setId("rev-1");

        when(reviewRepository.findById("rev-1")).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.deleteReview("prod-1", "rev-1"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ProductErrorCode.REVIEW_ACTION_FORBIDDEN));

        verify(reviewRepository, never()).delete(any());
    }

    @Test
    void getAdminReviews_returnsEnrichedUserDetailsAndProductName() {
        Product product = Product.builder().name("Áo Sơ Mi Nam").build();
        product.setId("prod-10");

        Review review = Review.builder()
                .product(product)
                .userId("customer-uuid-123")
                .orderId("order-uuid-456")
                .rating(5)
                .comment("Hàng chuẩn đẹp")
                .verifiedPurchase(true)
                .status(ReviewStatus.VISIBLE)
                .build();
        review.setId("rev-10");

        Page<Review> page = new PageImpl<>(List.of(review), PageRequest.of(0, 20), 1);
        when(reviewRepository.findAllForAdmin(eq("Sơ Mi"), eq(5), eq(ReviewStatus.VISIBLE), any(Pageable.class))).thenReturn(page);
        when(identityUserClient.getUserById("customer-uuid-123")).thenReturn(Optional.of(
                new IdentityUserClient.UserInfoResponse("customer-uuid-123", "nguyen@example.com", "Nguyễn Văn A", "0901234567")
        ));

        com.fashionstore.common.dto.PageResponse<List<ReviewResponse>> result =
                service.getAdminReviews("Sơ Mi", 5, ReviewStatus.VISIBLE, PageRequest.of(0, 20));

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1L);
        assertThat(result.getItems()).hasSize(1);

        ReviewResponse response = result.getItems().get(0);
        assertThat(response.getId()).isEqualTo("rev-10");
        assertThat(response.getProductId()).isEqualTo("prod-10");
        assertThat(response.getProductName()).isEqualTo("Áo Sơ Mi Nam");
        assertThat(response.getUserId()).isEqualTo("customer-uuid-123");
        assertThat(response.getCustomerName()).isEqualTo("Nguyễn Văn A");
        assertThat(response.getCustomerEmail()).isEqualTo("nguyen@example.com");
        assertThat(response.getCustomerPhone()).isEqualTo("0901234567");
        assertThat(response.getRating()).isEqualTo(5);
        assertThat(response.getStatus()).isEqualTo(ReviewStatus.VISIBLE);
    }
}
