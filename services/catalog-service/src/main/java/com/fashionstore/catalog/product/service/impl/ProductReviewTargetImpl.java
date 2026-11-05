package com.fashionstore.catalog.product.service.impl;

import com.fashionstore.catalog.product.api.ProductReviewTarget;
import com.fashionstore.catalog.product.entity.ProductVariant;
import com.fashionstore.catalog.product.exception.ProductErrorCode;
import com.fashionstore.catalog.product.repository.ProductRepository;
import com.fashionstore.catalog.product.repository.ProductVariantRepository;
import com.fashionstore.common.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductReviewTargetImpl implements ProductReviewTarget {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;

    @Override
    @Transactional(readOnly = true)
    public void requireExists(String productId) {
        if (!productRepository.existsById(productId)) {
            throw new AppException(ProductErrorCode.PRODUCT_NOT_FOUND);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> variantIds(String productId) {
        return productVariantRepository.findByProductId(productId).stream()
                .map(ProductVariant::getId)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> namesByIds(Collection<String> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productRepository.findNamesByIds(productIds).stream()
                .collect(Collectors.toMap(ProductRepository.ProductName::getId, ProductRepository.ProductName::getName));
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> idsByNameContaining(String keyword) {
        return productRepository.findIdsByNameContaining(keyword);
    }

    @Override
    @Transactional
    public void updateRating(String productId, BigDecimal average, int count) {
        productRepository.updateRating(productId, average, count);
    }
}
