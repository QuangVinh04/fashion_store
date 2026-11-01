package com.fashionstore.catalog.product.service.impl;

import com.fashionstore.catalog.product.dto.RegionFitResponse;
import com.fashionstore.catalog.product.dto.RegionFitResponse.Fit;
import com.fashionstore.catalog.product.dto.RegionFitResponse.Region;
import com.fashionstore.catalog.product.dto.SizeRecommendationRequest;
import com.fashionstore.catalog.product.dto.SizeRecommendationRequest.Preference;
import com.fashionstore.catalog.product.dto.SizeRecommendationResponse;
import com.fashionstore.catalog.product.dto.SizeRecommendationResponse.Basis;
import com.fashionstore.catalog.product.dto.SizeRecommendationResponse.Confidence;
import com.fashionstore.catalog.product.dto.SizeRecommendationResponse.Status;
import com.fashionstore.catalog.product.entity.ProductType;
import com.fashionstore.catalog.product.entity.SizeChartRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the pure fit-scoring rules, independent of persistence.
 */
class SizeFitCalculatorTest {

    private final SizeFitCalculator calculator = new SizeFitCalculator();

    private static final Set<String> AVAILABLE = Set.of("S", "M", "L", "XL");

    private static SizeChartRow row(String code, String chest, String shoulder, String waist,
                                    String heightMin, String heightMax, String weightMin, String weightMax) {
        return SizeChartRow.builder()
                .sizeCode(code)
                .chest(decimal(chest))
                .shoulder(decimal(shoulder))
                .waist(decimal(waist))
                .heightMin(decimal(heightMin))
                .heightMax(decimal(heightMax))
                .weightMin(decimal(weightMin))
                .weightMax(decimal(weightMax))
                .build();
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static List<SizeChartRow> topChart() {
        return List.of(
                row("S", "88", "42", "76", "160", "168", "50", "58"),
                row("M", "94", "44", "82", "167", "174", "57", "65"),
                row("L", "100", "46", "88", "173", "180", "64", "73"),
                row("XL", "106", "48", "94", "179", "186", "72", "82"));
    }

    private static SizeRecommendationRequest measurements(String chest, String shoulder, Preference preference) {
        return new SizeRecommendationRequest(null, null, decimal(chest), null, null, decimal(shoulder), null, null,
                preference);
    }

    private static SizeRecommendationRequest heightWeight(String height, String weight, Preference preference) {
        return new SizeRecommendationRequest(decimal(height), decimal(weight), null, null, null, null, null, null,
                preference);
    }

    private SizeRecommendationResponse recommendTop(List<SizeChartRow> rows, SizeRecommendationRequest request) {
        return calculator.recommend(rows, BigDecimal.ONE, ProductType.T_SHIRT, AVAILABLE, request);
    }

    private static RegionFitResponse region(Region region, Fit fit, String difference) {
        return new RegionFitResponse(region, fit, new BigDecimal(difference));
    }

    @Test
    void measurementsWithinToleranceRecommendMWithHighConfidence() {
        SizeRecommendationResponse result = recommendTop(topChart(), measurements("95", "44", Preference.REGULAR));

        assertThat(result.status()).isEqualTo(Status.RECOMMENDED);
        assertThat(result.recommendedSize()).isEqualTo("M");
        assertThat(result.alternativeSize()).isNull();
        assertThat(result.recommendedSizeAvailable()).isTrue();
        assertThat(result.basis()).isEqualTo(Basis.BODY_MEASUREMENTS);
        assertThat(result.confidence()).isEqualTo(Confidence.HIGH);
        assertThat(result.regions()).containsExactly(
                region(Region.CHEST, Fit.GOOD, "-1.0"),
                region(Region.SHOULDER, Fit.GOOD, "0.0"));
    }

    @Test
    void closeSecondBestBecomesAlternative() {
        SizeRecommendationResponse result = recommendTop(topChart(), measurements("97", "45", Preference.REGULAR));

        assertThat(result.recommendedSize()).isEqualTo("L");
        assertThat(result.alternativeSize()).isEqualTo("M");
    }

    @Test
    void snugPreferenceShiftsTowardSmallerSizeButReportsRawDifference() {
        SizeRecommendationResponse result = recommendTop(topChart(), measurements("97", "45", Preference.SNUG));

        assertThat(result.recommendedSize()).isEqualTo("M");
        assertThat(result.regions()).contains(region(Region.CHEST, Fit.GOOD, "-3.0"));
    }

    @Test
    void bodyLargerThanEverySizeStillRecommendsLargestAsTight() {
        SizeRecommendationResponse result = recommendTop(topChart(), measurements("115", null, Preference.REGULAR));

        assertThat(result.recommendedSize()).isEqualTo("XL");
        assertThat(result.confidence()).isEqualTo(Confidence.MEDIUM);
        assertThat(result.regions()).containsExactly(region(Region.CHEST, Fit.TIGHT, "-9.0"));
    }

    @Test
    void equalScoresGoToTheLargerSizeForRegular() {
        List<SizeChartRow> rows = List.of(
                row("S", "90", null, null, null, null, null, null),
                row("M", "95", null, null, null, null, null, null));

        SizeRecommendationResponse result = recommendTop(rows, measurements("92", null, Preference.REGULAR));

        assertThat(result.recommendedSize()).isEqualTo("M");
    }

    @Test
    void heightWeightInsideOneRangeRecommendsItWithMediumConfidence() {
        SizeRecommendationResponse result = recommendTop(topChart(), heightWeight("170", "60", Preference.REGULAR));

        assertThat(result.recommendedSize()).isEqualTo("M");
        assertThat(result.alternativeSize()).isNull();
        assertThat(result.basis()).isEqualTo(Basis.HEIGHT_WEIGHT);
        assertThat(result.confidence()).isEqualTo(Confidence.MEDIUM);
        assertThat(result.regions()).isEmpty();
    }

    @Test
    void heightWeightOnTwoRangesPicksBySizePreference() {
        SizeRecommendationResponse regular = recommendTop(topChart(), heightWeight("173.5", "64.5", Preference.REGULAR));
        SizeRecommendationResponse snug = recommendTop(topChart(), heightWeight("173.5", "64.5", Preference.SNUG));

        assertThat(regular.recommendedSize()).isEqualTo("L");
        assertThat(regular.alternativeSize()).isEqualTo("M");
        assertThat(snug.recommendedSize()).isEqualTo("M");
        assertThat(snug.alternativeSize()).isEqualTo("L");
    }

    @Test
    void loosePreferenceMovesUpOneSize() {
        SizeRecommendationResponse result = recommendTop(topChart(), heightWeight("170", "60", Preference.LOOSE));

        assertThat(result.recommendedSize()).isEqualTo("L");
        assertThat(result.alternativeSize()).isEqualTo("M");
    }

    @Test
    void outsideEveryRangeRecommendsNearestWithLowConfidence() {
        SizeRecommendationResponse result = recommendTop(topChart(), heightWeight("195", "95", Preference.REGULAR));

        assertThat(result.recommendedSize()).isEqualTo("XL");
        assertThat(result.confidence()).isEqualTo(Confidence.LOW);
    }

    @Test
    void measurementsTakePrecedenceOverHeightWeight() {
        SizeRecommendationRequest request = new SizeRecommendationRequest(decimal("170"), decimal("60"),
                decimal("101"), null, null, null, null, null, Preference.REGULAR);

        SizeRecommendationResponse result = recommendTop(topChart(), request);

        assertThat(result.basis()).isEqualTo(Basis.BODY_MEASUREMENTS);
        assertThat(result.recommendedSize()).isEqualTo("L");
    }

    @Test
    void topIgnoresHipAndWithoutHeightWeightReportsNotEnoughData() {
        SizeRecommendationRequest request = new SizeRecommendationRequest(null, null, null, null, decimal("95"),
                null, null, null, Preference.REGULAR);

        SizeRecommendationResponse result = recommendTop(topChart(), request);

        assertThat(result.status()).isEqualTo(Status.NOT_ENOUGH_DATA);
        assertThat(result.regions()).isEmpty();
    }

    @Test
    void rowsWithoutRangesCannotServeHeightWeight() {
        List<SizeChartRow> rows = List.of(
                row("S", "88", null, null, null, null, null, null),
                row("M", "94", null, null, null, null, null, null));

        SizeRecommendationResponse result = recommendTop(rows, heightWeight("170", "60", Preference.REGULAR));

        assertThat(result.status()).isEqualTo(Status.NOT_ENOUGH_DATA);
    }
}
