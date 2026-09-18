package com.fashionstore.catalog.controller;

import com.fashionstore.catalog.dto.review.CreateReviewRequest;
import com.fashionstore.catalog.dto.review.ProductReviewSummaryResponse;
import com.fashionstore.catalog.dto.review.ReviewResponse;
import com.fashionstore.catalog.service.ReviewService;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReviewControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ReviewService reviewService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReviewController(reviewService))
                .setCustomArgumentResolvers(new org.springframework.data.web.PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getProductReviews_withValidRating_returns200() throws Exception {
        ProductReviewSummaryResponse summary = ProductReviewSummaryResponse.builder()
                .averageRating(4.5)
                .totalReviews(10L)
                .ratingCounts(Map.of(5, 7L, 4, 3L))
                .reviews(PageResponse.<List<ReviewResponse>>builder()
                        .pageNo(0)
                        .pageSize(10)
                        .totalPage(1)
                        .items(List.of(ReviewResponse.builder()
                                .id("rev-1")
                                .productId("prod-1")
                                .userId("u***1")
                                .rating(5)
                                .build()))
                        .build())
                .build();

        when(reviewService.getProductReviews(eq("prod-1"), eq(5), any(Pageable.class))).thenReturn(summary);

        mockMvc.perform(get("/api/v1/products/prod-1/reviews")
                        .param("rating", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageRating").value(4.5))
                .andExpect(jsonPath("$.data.totalReviews").value(10))
                .andExpect(jsonPath("$.data.reviews.items[0].userId").value("u***1"));
    }

    @Test
    void getProductReviews_withInvalidRatingBelowMin_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/products/prod-1/reviews")
                        .param("rating", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getProductReviews_withInvalidRatingAboveMax_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/products/prod-1/reviews")
                        .param("rating", "6"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReview_validRequest_returns201Created() throws Exception {
        CreateReviewRequest request = CreateReviewRequest.builder()
                .rating(5)
                .comment("Sản phẩm rất đẹp và chất lượng")
                .orderId("ord-1")
                .build();

        ReviewResponse response = ReviewResponse.builder()
                .id("rev-100")
                .productId("prod-1")
                .userId("user-1")
                .orderId("ord-1")
                .rating(5)
                .comment("Sản phẩm rất đẹp và chất lượng")
                .verifiedPurchase(true)
                .createdAt(LocalDateTime.now())
                .build();

        when(reviewService.createReview(eq("prod-1"), any(CreateReviewRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/products/prod-1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("rev-100"))
                .andExpect(jsonPath("$.data.rating").value(5));
    }

    @Test
    void createReview_withInvalidRatingInBody_returns400() throws Exception {
        CreateReviewRequest request = CreateReviewRequest.builder()
                .rating(0)
                .comment("Tệ")
                .build();

        mockMvc.perform(post("/api/v1/products/prod-1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteReview_returns200() throws Exception {
        mockMvc.perform(delete("/api/v1/products/prod-1/reviews/rev-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Review deleted successfully"));

        verify(reviewService).deleteReview("prod-1", "rev-100");
    }
}
