package com.fashionstore.catalog.product.service.impl;

import com.fashionstore.catalog.product.dto.ProductResponse;
import com.fashionstore.catalog.product.dto.ProductVariantResponse;
import com.fashionstore.catalog.product.dto.SizeRecommendationRequest;
import com.fashionstore.catalog.product.dto.SizeRecommendationResponse;
import com.fashionstore.catalog.product.entity.SizeChart;
import com.fashionstore.catalog.product.entity.SizeChartRow;
import com.fashionstore.catalog.product.exception.ProductErrorCode;
import com.fashionstore.catalog.product.repository.SizeChartRepository;
import com.fashionstore.catalog.product.service.ProductService;
import com.fashionstore.catalog.product.service.SizeRecommendationService;
import com.fashionstore.common.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads the product and its size chart, keeps only the sizes the product sells, and delegates scoring.
 */
@Service
@RequiredArgsConstructor
public class SizeRecommendationServiceImpl implements SizeRecommendationService {

    private static final BigDecimal CENTIMETRES_PER_INCH = new BigDecimal("2.54");
    private static final Set<String> INCH_UNITS = Set.of("IN", "INCH", "INCHES");

    private final ProductService productService;
    private final SizeChartRepository sizeChartRepository;
    private final SizeFitCalculator calculator = new SizeFitCalculator();

    @Override
    @Transactional(readOnly = true)
    public SizeRecommendationResponse recommend(String productId, SizeRecommendationRequest request) {
        if (!request.hasAnyValue()) {
            throw new AppException(ProductErrorCode.SIZE_RECOMMENDATION_INPUT_REQUIRED);
        }
        ProductResponse product = productService.getProductById(productId);
        Optional<SizeChart> chart = findChart(product);
        if (chart.isEmpty()) {
            return SizeRecommendationResponse.notEnoughData();
        }
        Set<String> soldSizes = sizesOf(product, false);
        List<SizeChartRow> rows = chart.get().getRows().stream()
                .filter(row -> soldSizes.contains(SizeCodes.normalize(row.getSizeCode())))
                .toList();
        if (rows.isEmpty()) {
            return SizeRecommendationResponse.notEnoughData();
        }
        return calculator.recommend(rows, unitToCentimetres(chart.get()), product.getProductType(),
                sizesOf(product, true), request);
    }

    private Optional<SizeChart> findChart(ProductResponse product) {
        String chartId = product.getSizeChartId();
        if (chartId == null || chartId.isBlank()) {
            return Optional.empty();
        }
        return sizeChartRepository.findDetailById(chartId);
    }

    private Set<String> sizesOf(ProductResponse product, boolean onlyInStock) {
        if (product.getVariants() == null) {
            return Set.of();
        }
        return product.getVariants().stream()
                .filter(variant -> Boolean.TRUE.equals(variant.getActive()))
                .filter(variant -> !onlyInStock || Boolean.TRUE.equals(variant.getAvailable()))
                .map(ProductVariantResponse::getSize)
                .map(SizeCodes::normalize)
                .collect(Collectors.toSet());
    }

    private BigDecimal unitToCentimetres(SizeChart chart) {
        return INCH_UNITS.contains(SizeCodes.normalize(chart.getUnit()))
                ? CENTIMETRES_PER_INCH
                : BigDecimal.ONE;
    }
}
