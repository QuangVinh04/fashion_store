package com.fashionstore.catalog.product.dto;

import java.math.BigDecimal;

/**
 * How one body region fits in the recommended size; differenceCm is size measurement minus body measurement.
 */
public record RegionFitResponse(Region region, Fit fit, BigDecimal differenceCm) {

    public enum Region { CHEST, WAIST, HIP, SHOULDER, LENGTH, INSEAM }

    public enum Fit { TIGHT, SNUG, GOOD, LOOSE }
}
