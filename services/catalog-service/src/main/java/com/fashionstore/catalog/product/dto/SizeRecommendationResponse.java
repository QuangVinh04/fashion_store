package com.fashionstore.catalog.product.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Size suggestion for one product; everything except status and regions is null when data is not enough.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SizeRecommendationResponse(
        Status status,
        String recommendedSize,
        String alternativeSize,
        Boolean recommendedSizeAvailable,
        Basis basis,
        Confidence confidence,
        List<RegionFitResponse> regions) {

    public enum Status { RECOMMENDED, NOT_ENOUGH_DATA }

    public enum Basis { BODY_MEASUREMENTS, HEIGHT_WEIGHT }

    public enum Confidence { HIGH, MEDIUM, LOW }

    public static SizeRecommendationResponse notEnoughData() {
        return new SizeRecommendationResponse(Status.NOT_ENOUGH_DATA, null, null, null, null, null, List.of());
    }
}
