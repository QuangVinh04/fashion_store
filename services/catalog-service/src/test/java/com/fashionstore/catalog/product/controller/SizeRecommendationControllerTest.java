package com.fashionstore.catalog.product.controller;

import com.fashionstore.catalog.product.dto.SizeRecommendationRequest;
import com.fashionstore.catalog.product.dto.SizeRecommendationRequest.Preference;
import com.fashionstore.catalog.product.dto.SizeRecommendationResponse;
import com.fashionstore.catalog.product.service.SizeRecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies query-parameter binding and validation of the size recommendation endpoint.
 */
@ExtendWith(MockitoExtension.class)
class SizeRecommendationControllerTest {

    private static final String URL = "/api/v1/products/p1/size-recommendation";

    @Mock
    private SizeRecommendationService sizeRecommendationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SizeRecommendationController(sizeRecommendationService)).build();
    }

    @Test
    void queryParametersAreBoundIntoTheRequest() throws Exception {
        when(sizeRecommendationService.recommend(eq("p1"), any()))
                .thenReturn(SizeRecommendationResponse.notEnoughData());

        mockMvc.perform(get(URL).param("heightCm", "170").param("weightKg", "60").param("preference", "SNUG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NOT_ENOUGH_DATA"));

        ArgumentCaptor<SizeRecommendationRequest> captor = ArgumentCaptor.forClass(SizeRecommendationRequest.class);
        verify(sizeRecommendationService).recommend(eq("p1"), captor.capture());
        assertThat(captor.getValue().heightCm()).isEqualByComparingTo(new BigDecimal("170"));
        assertThat(captor.getValue().weightKg()).isEqualByComparingTo(new BigDecimal("60"));
        assertThat(captor.getValue().preference()).isEqualTo(Preference.SNUG);
    }

    @Test
    void outOfRangeHeightIsBadRequest() throws Exception {
        mockMvc.perform(get(URL).param("heightCm", "300")).andExpect(status().isBadRequest());

        verify(sizeRecommendationService, never()).recommend(any(), any());
    }
}
