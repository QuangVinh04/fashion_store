package com.fashionstore.catalog.product.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/**
 * Body data a shopper supplies to get a size suggestion. Every field is optional, but at least one is required.
 */
public record SizeRecommendationRequest(
        @DecimalMin("100") @DecimalMax("230") BigDecimal heightCm,
        @DecimalMin("25") @DecimalMax("250") BigDecimal weightKg,
        @DecimalMin("50") @DecimalMax("180") BigDecimal chestCm,
        @DecimalMin("40") @DecimalMax("180") BigDecimal waistCm,
        @DecimalMin("50") @DecimalMax("180") BigDecimal hipCm,
        @DecimalMin("25") @DecimalMax("70") BigDecimal shoulderCm,
        @DecimalMin("40") @DecimalMax("130") BigDecimal lengthCm,
        @DecimalMin("40") @DecimalMax("110") BigDecimal inseamCm,
        Preference preference) {

    public enum Preference { SNUG, REGULAR, LOOSE }

    public Preference effectivePreference() {
        return preference == null ? Preference.REGULAR : preference;
    }

    public boolean hasAnyValue() {
        return heightCm != null || weightKg != null || chestCm != null || waistCm != null || hipCm != null
                || shoulderCm != null || lengthCm != null || inseamCm != null;
    }
}
