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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scores size chart rows against a shopper's body data. Pure logic: rows arrive already filtered to the sizes
 * the product sells, and measurements are converted to centimetres by the caller through unitToCm.
 */
final class SizeFitCalculator {

    private static final double TIGHT_BELOW = -4;
    private static final double GOOD_FROM = -1;
    private static final double GOOD_UP_TO = 4;
    private static final double TIGHT_PENALTY_FACTOR = 1.5;
    private static final double ALTERNATIVE_MARGIN_MEASUREMENT = 6;
    private static final double SNUG_OFFSET_CM = -2;
    private static final double LOOSE_OFFSET_CM = 4;
    private static final double HEIGHT_GAP_SCALE_CM = 5;
    private static final double WEIGHT_GAP_SCALE_KG = 3;
    private static final double ALTERNATIVE_MARGIN_HEIGHT_WEIGHT = 1;
    private static final int MIN_REGIONS_FOR_HIGH_CONFIDENCE = 2;
    private static final int DIFFERENCE_SCALE = 1;

    private static final Map<Region, Integer> TOP_WEIGHTS = Map.of(
            Region.CHEST, 3, Region.SHOULDER, 2, Region.WAIST, 1, Region.LENGTH, 1);
    private static final Map<Region, Integer> BOTTOM_WEIGHTS = Map.of(
            Region.WAIST, 3, Region.HIP, 2, Region.INSEAM, 1, Region.LENGTH, 1);
    private static final Map<Region, Integer> FULL_BODY_WEIGHTS = Map.of(
            Region.CHEST, 2, Region.WAIST, 2, Region.HIP, 2, Region.SHOULDER, 1, Region.LENGTH, 1, Region.INSEAM, 1);

    private record Candidate(SizeChartRow row, double score, List<RegionFitResponse> regions) {
    }

    SizeRecommendationResponse recommend(List<SizeChartRow> rows, BigDecimal unitToCm, ProductType productType,
                                         Set<String> availableSizeCodes, SizeRecommendationRequest request) {
        Preference preference = request.effectivePreference();
        List<Candidate> byMeasurements = scoreByMeasurements(rows, unitToCm, productType, request, preference);
        if (!byMeasurements.isEmpty()) {
            return bodyResponse(byMeasurements, availableSizeCodes, preference);
        }
        List<Candidate> byHeightWeight = scoreByHeightWeight(rows, unitToCm, request, preference);
        if (!byHeightWeight.isEmpty()) {
            return heightWeightResponse(byHeightWeight, availableSizeCodes, preference);
        }
        return SizeRecommendationResponse.notEnoughData();
    }

    private List<Candidate> scoreByMeasurements(List<SizeChartRow> rows, BigDecimal unitToCm, ProductType productType,
                                                SizeRecommendationRequest request, Preference preference) {
        Map<Region, Integer> weights = weightsFor(productType);
        List<Candidate> candidates = new ArrayList<>();
        for (SizeChartRow row : rows) {
            Candidate candidate = scoreRow(row, unitToCm, weights, request, preference);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score).thenComparing(tieBreaker(preference)));
        return candidates;
    }

    private Candidate scoreRow(SizeChartRow row, BigDecimal unitToCm, Map<Region, Integer> weights,
                               SizeRecommendationRequest request, Preference preference) {
        double score = 0;
        List<RegionFitResponse> regions = new ArrayList<>();
        for (Region region : Region.values()) {
            BigDecimal rowValue = rowValue(row, region);
            BigDecimal bodyValue = bodyValue(request, region);
            if (!weights.containsKey(region) || rowValue == null || bodyValue == null) {
                continue;
            }
            BigDecimal difference = rowValue.multiply(unitToCm).subtract(bodyValue);
            double adjusted = difference.doubleValue() - offset(preference);
            score += weights.get(region) * penalty(adjusted);
            regions.add(new RegionFitResponse(region, classify(adjusted),
                    difference.setScale(DIFFERENCE_SCALE, RoundingMode.HALF_UP)));
        }
        return regions.isEmpty() ? null : new Candidate(row, score, regions);
    }

    private SizeRecommendationResponse bodyResponse(List<Candidate> ranked, Set<String> availableSizeCodes,
                                                    Preference preference) {
        Candidate best = ranked.get(0);
        String alternative = alternativeWithin(ranked, ALTERNATIVE_MARGIN_MEASUREMENT);
        boolean hasTightRegion = best.regions().stream().anyMatch(region -> region.fit() == Fit.TIGHT);
        Confidence confidence = best.regions().size() >= MIN_REGIONS_FOR_HIGH_CONFIDENCE && !hasTightRegion
                ? Confidence.HIGH
                : Confidence.MEDIUM;
        String recommended = SizeCodes.normalize(best.row().getSizeCode());
        return new SizeRecommendationResponse(Status.RECOMMENDED, recommended, alternative,
                availableSizeCodes.contains(recommended), Basis.BODY_MEASUREMENTS, confidence, best.regions());
    }

    private List<Candidate> scoreByHeightWeight(List<SizeChartRow> rows, BigDecimal unitToCm,
                                                SizeRecommendationRequest request, Preference preference) {
        List<Candidate> candidates = new ArrayList<>();
        for (SizeChartRow row : rows) {
            Double heightGap = gap(request.heightCm(), row.getHeightMin(), row.getHeightMax(), unitToCm);
            Double weightGap = gap(request.weightKg(), row.getWeightMin(), row.getWeightMax(), BigDecimal.ONE);
            if (heightGap == null && weightGap == null) {
                continue;
            }
            double score = (heightGap == null ? 0 : heightGap / HEIGHT_GAP_SCALE_CM)
                    + (weightGap == null ? 0 : weightGap / WEIGHT_GAP_SCALE_KG);
            candidates.add(new Candidate(row, score, List.of()));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score).thenComparing(tieBreaker(preference)));
        return candidates;
    }

    /** Distance outside the row's range; null when the shopper gave no value or the row has no range. */
    private Double gap(BigDecimal value, BigDecimal min, BigDecimal max, BigDecimal unitToCm) {
        if (value == null || (min == null && max == null)) {
            return null;
        }
        double below = min == null ? 0 : min.multiply(unitToCm).subtract(value).doubleValue();
        double above = max == null ? 0 : value.subtract(max.multiply(unitToCm)).doubleValue();
        return Math.max(0, Math.max(below, above));
    }

    private SizeRecommendationResponse heightWeightResponse(List<Candidate> ranked, Set<String> availableSizeCodes,
                                                            Preference preference) {
        Candidate best = ranked.get(0);
        String recommended = SizeCodes.normalize(best.row().getSizeCode());
        String alternative = alternativeWithin(ranked, ALTERNATIVE_MARGIN_HEIGHT_WEIGHT);
        String nextLarger = preference == Preference.LOOSE ? nextLargerSize(ranked, recommended) : null;
        if (nextLarger != null) {
            alternative = recommended;
            recommended = nextLarger;
        }
        Confidence confidence = best.score() == 0 ? Confidence.MEDIUM : Confidence.LOW;
        return new SizeRecommendationResponse(Status.RECOMMENDED, recommended, alternative,
                availableSizeCodes.contains(recommended), Basis.HEIGHT_WEIGHT, confidence, List.of());
    }

    private String nextLargerSize(List<Candidate> ranked, String sizeCode) {
        return ranked.stream()
                .map(candidate -> SizeCodes.normalize(candidate.row().getSizeCode()))
                .filter(code -> SizeCodes.SMALLEST_FIRST.compare(code, sizeCode) > 0)
                .min(SizeCodes.SMALLEST_FIRST)
                .orElse(null);
    }

    private String alternativeWithin(List<Candidate> ranked, double margin) {
        if (ranked.size() < 2 || ranked.get(1).score() > ranked.get(0).score() + margin) {
            return null;
        }
        return SizeCodes.normalize(ranked.get(1).row().getSizeCode());
    }

    /** Equal scores: a snug shopper gets the smaller size, everyone else the larger one. */
    private Comparator<Candidate> tieBreaker(Preference preference) {
        Comparator<Candidate> smallestFirst = Comparator.comparing(
                candidate -> candidate.row().getSizeCode(), SizeCodes.SMALLEST_FIRST);
        return preference == Preference.SNUG ? smallestFirst : smallestFirst.reversed();
    }

    private double offset(Preference preference) {
        return switch (preference) {
            case SNUG -> SNUG_OFFSET_CM;
            case LOOSE -> LOOSE_OFFSET_CM;
            case REGULAR -> 0;
        };
    }

    private double penalty(double adjustedDifference) {
        return adjustedDifference < 0 ? TIGHT_PENALTY_FACTOR * -adjustedDifference : adjustedDifference;
    }

    private Fit classify(double adjustedDifference) {
        if (adjustedDifference < TIGHT_BELOW) {
            return Fit.TIGHT;
        }
        if (adjustedDifference < GOOD_FROM) {
            return Fit.SNUG;
        }
        return adjustedDifference <= GOOD_UP_TO ? Fit.GOOD : Fit.LOOSE;
    }

    private Map<Region, Integer> weightsFor(ProductType productType) {
        if (productType == null) {
            return FULL_BODY_WEIGHTS;
        }
        return switch (productType) {
            case T_SHIRT, SHIRT, POLO, HOODIE, SWEATER, JACKET -> TOP_WEIGHTS;
            case JEANS, PANTS, SHORTS, SKIRT -> BOTTOM_WEIGHTS;
            default -> FULL_BODY_WEIGHTS;
        };
    }

    private BigDecimal rowValue(SizeChartRow row, Region region) {
        return switch (region) {
            case CHEST -> row.getChest();
            case WAIST -> row.getWaist();
            case HIP -> row.getHip();
            case SHOULDER -> row.getShoulder();
            case LENGTH -> row.getLength();
            case INSEAM -> row.getInseam();
        };
    }

    private BigDecimal bodyValue(SizeRecommendationRequest request, Region region) {
        return switch (region) {
            case CHEST -> request.chestCm();
            case WAIST -> request.waistCm();
            case HIP -> request.hipCm();
            case SHOULDER -> request.shoulderCm();
            case LENGTH -> request.lengthCm();
            case INSEAM -> request.inseamCm();
        };
    }
}
