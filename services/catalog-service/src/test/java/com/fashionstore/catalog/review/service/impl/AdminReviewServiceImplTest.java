package com.fashionstore.catalog.review.service.impl;

import com.fashionstore.catalog.product.api.ProductReviewTarget;
import com.fashionstore.catalog.review.client.IdentityUserClient;
import com.fashionstore.catalog.review.dto.ReviewResponse;
import com.fashionstore.catalog.review.entity.Review;
import com.fashionstore.catalog.review.entity.ReviewStatus;
import com.fashionstore.catalog.review.exception.ReviewErrorCode;
import com.fashionstore.catalog.review.mapper.ReviewMapper;
import com.fashionstore.catalog.review.repository.ReviewRepository;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
class AdminReviewServiceImplTest {

    @Mock
    ReviewRepository reviewRepository;
    @Mock
    ProductReviewTarget productReviewTarget;
    @Mock
    IdentityUserClient identityUserClient;
    @Mock
    CurrentUserProvider currentUserProvider;
    @Mock
    ProductRatingSync productRatingSync;

    AdminReviewServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminReviewServiceImpl(reviewRepository, productReviewTarget, identityUserClient,
                currentUserProvider, productRatingSync, new ReviewMapper());
        when(currentUserProvider.getCurrentUserId()).thenReturn("staff-1");
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getReviews_resolvesProductNamesAndCustomerOnceForKeywordSearch() {
        Review first = ReviewServiceImplTest.review("rev-1", "customer-1");
        Review second = ReviewServiceImplTest.review("rev-2", "customer-1");
        Pageable pageable = PageRequest.of(0, 20);
        when(productReviewTarget.idsByNameContaining("Polo")).thenReturn(List.of("prod-1"));
        when(reviewRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(first, second), pageable, 2));
        when(productReviewTarget.namesByIds(Set.of("prod-1"))).thenReturn(Map.of("prod-1", "Áo Polo"));
        when(identityUserClient.getUserById("customer-1")).thenReturn(Optional.of(
                new IdentityUserClient.UserInfoResponse("customer-1", "a@example.com", "Nguyễn Văn A", "0901")));

        PageResponse<List<ReviewResponse>> page = service.getReviews("Polo", 5, ReviewStatus.VISIBLE, pageable);

        assertThat(page.getTotalElements()).isEqualTo(2L);
        ReviewResponse item = page.getItems().get(0);
        assertThat(item.getProductName()).isEqualTo("Áo Polo");
        assertThat(item.getCustomerName()).isEqualTo("Nguyễn Văn A");
        assertThat(item.getCustomerEmail()).isEqualTo("a@example.com");
        assertThat(item.getUserId()).isEqualTo("customer-1");
        verify(identityUserClient, times(1)).getUserById("customer-1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getReviews_withoutKeywordDoesNotSearchProducts() {
        Pageable pageable = PageRequest.of(0, 20);
        when(reviewRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.getReviews(" ", null, null, pageable);

        verify(productReviewTarget, never()).idsByNameContaining(any());
    }

    @Test
    void updateStatus_hidesReviewAndRefreshesRating() {
        Review review = ReviewServiceImplTest.review("rev-1", "customer-1");
        when(reviewRepository.findById("rev-1")).thenReturn(Optional.of(review));

        ReviewResponse response = service.updateStatus("rev-1", ReviewStatus.HIDDEN);

        assertThat(response.getStatus()).isEqualTo(ReviewStatus.HIDDEN);
        verify(productRatingSync).refresh("prod-1");
    }

    @Test
    void reply_recordsStaffAndTime() {
        Review review = ReviewServiceImplTest.review("rev-1", "customer-1");
        when(reviewRepository.findById("rev-1")).thenReturn(Optional.of(review));

        ReviewResponse response = service.reply("rev-1", "Cảm ơn bạn");

        assertThat(response.getReplyComment()).isEqualTo("Cảm ơn bạn");
        assertThat(review.getRepliedBy()).isEqualTo("staff-1");
        assertThat(review.getRepliedAt()).isNotNull();
        verify(productRatingSync, never()).refresh(any());
        assertThat(review.getEditedAt()).isNull();
    }

    @Test
    void deleteReply_clearsReplyFields() {
        Review review = ReviewServiceImplTest.review("rev-1", "customer-1");
        review.setReplyComment("Cảm ơn");
        review.setRepliedBy("staff-1");
        when(reviewRepository.findById("rev-1")).thenReturn(Optional.of(review));

        service.deleteReply("rev-1");

        assertThat(review.getReplyComment()).isNull();
        assertThat(review.getRepliedBy()).isNull();
        assertThat(review.getRepliedAt()).isNull();
    }

    @Test
    void delete_removesReviewAndRefreshesRating() {
        Review review = ReviewServiceImplTest.review("rev-1", "customer-1");
        when(reviewRepository.findById("rev-1")).thenReturn(Optional.of(review));

        service.delete("rev-1");

        verify(reviewRepository).delete(review);
        verify(productRatingSync).refresh("prod-1");
    }

    @Test
    void missingReview_throwsNotFound() {
        when(reviewRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("missing"))
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ReviewErrorCode.REVIEW_NOT_FOUND);
    }
}
