package com.fashionstore.catalog.product.service.impl;

import com.fashionstore.catalog.product.dto.ProductResponse;
import com.fashionstore.catalog.product.dto.ProductVariantResponse;
import com.fashionstore.catalog.product.dto.RegionFitResponse;
import com.fashionstore.catalog.product.dto.RegionFitResponse.Fit;
import com.fashionstore.catalog.product.dto.RegionFitResponse.Region;
import com.fashionstore.catalog.product.dto.SizeRecommendationRequest;
import com.fashionstore.catalog.product.dto.SizeRecommendationRequest.Preference;
import com.fashionstore.catalog.product.dto.SizeRecommendationResponse;
import com.fashionstore.catalog.product.dto.SizeRecommendationResponse.Status;
import com.fashionstore.catalog.product.entity.ProductType;
import com.fashionstore.catalog.product.entity.SizeChart;
import com.fashionstore.catalog.product.entity.SizeChartRow;
import com.fashionstore.catalog.product.exception.ProductErrorCode;
import com.fashionstore.catalog.product.repository.SizeChartRepository;
import com.fashionstore.catalog.product.service.ProductService;
import com.fashionstore.common.exception.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies how the service narrows a size chart down to what the product really sells.
 */
@ExtendWith(MockitoExtension.class)
class SizeRecommendationServiceImplTest {

    private static final String PRODUCT_ID = "p1";
    private static final String CHART_ID = "sc1";

    @Mock
    private ProductService productService;
    @Mock
    private SizeChartRepository sizeChartRepository;

    @InjectMocks
    private SizeRecommendationServiceImpl service;

    private static SizeChartRow row(String code, String chest, String heightMin, String heightMax,
                                    String weightMin, String weightMax) {
        return SizeChartRow.builder()
                .sizeCode(code)
                .chest(chest == null ? null : new BigDecimal(chest))
                .heightMin(heightMin == null ? null : new BigDecimal(heightMin))
                .heightMax(heightMax == null ? null : new BigDecimal(heightMax))
                .weightMin(weightMin == null ? null : new BigDecimal(weightMin))
                .weightMax(weightMax == null ? null : new BigDecimal(weightMax))
                .build();
    }

    private static List<SizeChartRow> chartRows() {
        return List.of(
                row("S", "88", "160", "168", "50", "58"),
                row("M", "94", "167", "174", "57", "65"),
                row("L", "100", "173", "180", "64", "73"));
    }

    private static ProductVariantResponse variant(String size, boolean active, boolean available) {
        return ProductVariantResponse.builder().size(size).active(active).available(available).build();
    }

    private void givenProduct(String sizeChartId, ProductVariantResponse... variants) {
        when(productService.getProductById(PRODUCT_ID)).thenReturn(ProductResponse.builder()
                .productType(ProductType.T_SHIRT)
                .sizeChartId(sizeChartId)
                .variants(List.of(variants))
                .build());
    }

    private void givenChart(String unit, List<SizeChartRow> rows) {
        SizeChart chart = SizeChart.builder().name("Tops").unit(unit).rows(rows).build();
        when(sizeChartRepository.findDetailById(CHART_ID)).thenReturn(Optional.of(chart));
    }

    private static SizeRecommendationRequest heightWeight(String height, String weight) {
        return new SizeRecommendationRequest(new BigDecimal(height), new BigDecimal(weight),
                null, null, null, null, null, null, Preference.REGULAR);
    }

    @Test
    void emptyRequestIsRejected() {
        SizeRecommendationRequest empty = new SizeRecommendationRequest(null, null, null, null, null, null, null,
                null, null);

        assertThatThrownBy(() -> service.recommend(PRODUCT_ID, empty))
                .isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ProductErrorCode.SIZE_RECOMMENDATION_INPUT_REQUIRED));
        verifyNoInteractions(productService);
    }

    @Test
    void productWithoutSizeChartReportsNotEnoughData() {
        givenProduct(null, variant("M", true, true));

        SizeRecommendationResponse result = service.recommend(PRODUCT_ID, heightWeight("170", "60"));

        assertThat(result.status()).isEqualTo(Status.NOT_ENOUGH_DATA);
    }

    @Test
    void missingSizeChartReportsNotEnoughData() {
        givenProduct(CHART_ID, variant("M", true, true));
        when(sizeChartRepository.findDetailById(CHART_ID)).thenReturn(Optional.empty());

        SizeRecommendationResponse result = service.recommend(PRODUCT_ID, heightWeight("170", "60"));

        assertThat(result.status()).isEqualTo(Status.NOT_ENOUGH_DATA);
    }

    @Test
    void onlySizesTheProductSellsAreRecommended() {
        givenProduct(CHART_ID, variant("S", false, false), variant("M", true, true), variant("L", true, true));
        givenChart("cm", chartRows());

        SizeRecommendationResponse result = service.recommend(PRODUCT_ID, heightWeight("162", "52"));

        assertThat(result.recommendedSize()).isEqualTo("M");
    }

    @Test
    void sizeCodeMatchingIgnoresCaseAndWhitespace() {
        givenProduct(CHART_ID, variant("M", true, true));
        givenChart("cm", List.of(row(" m ", "94", "167", "174", "57", "65")));

        SizeRecommendationResponse result = service.recommend(PRODUCT_ID, heightWeight("170", "60"));

        assertThat(result.status()).isEqualTo(Status.RECOMMENDED);
        assertThat(result.recommendedSize()).isEqualTo("M");
    }

    @Test
    void soldOutRecommendedSizeIsFlagged() {
        givenProduct(CHART_ID, variant("M", true, false), variant("L", true, true));
        givenChart("cm", chartRows());

        SizeRecommendationResponse result = service.recommend(PRODUCT_ID, heightWeight("170", "60"));

        assertThat(result.recommendedSize()).isEqualTo("M");
        assertThat(result.recommendedSizeAvailable()).isFalse();
    }

    @Test
    void inchChartIsConvertedToCentimetres() {
        givenProduct(CHART_ID, variant("M", true, true));
        givenChart("inch", List.of(row("M", "37", null, null, null, null)));
        SizeRecommendationRequest request = new SizeRecommendationRequest(null, null, new BigDecimal("94"),
                null, null, null, null, null, Preference.REGULAR);

        SizeRecommendationResponse result = service.recommend(PRODUCT_ID, request);

        assertThat(result.regions())
                .containsExactly(new RegionFitResponse(Region.CHEST, Fit.GOOD, new BigDecimal("0.0")));
    }
}
