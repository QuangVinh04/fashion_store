package com.fashionstore.catalog.controller;

import com.fashionstore.catalog.dto.review.ReplyReviewRequest;
import com.fashionstore.catalog.dto.review.ReviewResponse;
import com.fashionstore.catalog.dto.review.UpdateReviewStatusRequest;
import com.fashionstore.catalog.entity.enumeration.ReviewStatus;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminReviewControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminReviewController(reviewService))
                .setCustomArgumentResolvers(new org.springframework.data.web.PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void list_withValidParams_returns200AndDataShape() throws Exception {
        PageResponse<List<ReviewResponse>> response = PageResponse.<List<ReviewResponse>>builder()
                .pageNo(0)
                .pageSize(20)
                .totalPage(1)
                .totalElements(1L)
                .items(List.of(ReviewResponse.builder()
                        .id("rev-1")
                        .productId("prod-1")
                        .productName("Áo Polo")
                        .userId("user-123")
                        .customerName("Nguyễn Văn A")
                        .orderId("ord-456")
                        .rating(5)
                        .status(ReviewStatus.VISIBLE)
                        .comment("Rất đẹp")
                        .verifiedPurchase(true)
                        .createdAt(LocalDateTime.now())
                        .build()))
                .build();

        when(reviewService.getAdminReviews(eq("Polo"), eq(5), eq(ReviewStatus.VISIBLE), any(Pageable.class))).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/reviews")
                        .param("keyword", "Polo")
                        .param("rating", "5")
                        .param("status", "VISIBLE")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value("rev-1"))
                .andExpect(jsonPath("$.data.items[0].productName").value("Áo Polo"))
                .andExpect(jsonPath("$.data.items[0].customerName").value("Nguyễn Văn A"))
                .andExpect(jsonPath("$.data.items[0].status").value("VISIBLE"));
    }

    @Test
    void updateStatus_returns200() throws Exception {
        ReviewResponse response = ReviewResponse.builder()
                .id("rev-1")
                .status(ReviewStatus.HIDDEN)
                .build();

        when(reviewService.updateReviewStatus("rev-1", ReviewStatus.HIDDEN)).thenReturn(response);

        mockMvc.perform(patch("/api/v1/admin/reviews/rev-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateReviewStatusRequest(ReviewStatus.HIDDEN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HIDDEN"));
    }

    @Test
    void reply_returns200() throws Exception {
        ReviewResponse response = ReviewResponse.builder()
                .id("rev-1")
                .replyComment("Cảm ơn quý khách")
                .build();

        when(reviewService.replyReview("rev-1", "Cảm ơn quý khách")).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/reviews/rev-1/reply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReplyReviewRequest("Cảm ơn quý khách"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyComment").value("Cảm ơn quý khách"));
    }

    @Test
    void deleteReply_returns200() throws Exception {
        ReviewResponse response = ReviewResponse.builder()
                .id("rev-1")
                .replyComment(null)
                .build();

        when(reviewService.deleteReply("rev-1")).thenReturn(response);

        mockMvc.perform(delete("/api/v1/admin/reviews/rev-1/reply"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteReview_returns200() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/reviews/rev-1"))
                .andExpect(status().isOk());

        verify(reviewService).adminDeleteReview("rev-1");
    }

    @Test
    void list_withInvalidRatingBelowMin_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reviews")
                        .param("rating", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_withInvalidRatingAboveMax_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reviews")
                        .param("rating", "6"))
                .andExpect(status().isBadRequest());
    }
}
