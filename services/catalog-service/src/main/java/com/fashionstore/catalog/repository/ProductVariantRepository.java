package com.fashionstore.catalog.repository;


import com.fashionstore.catalog.model.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, String> {
    @EntityGraph(attributePaths = {"product"})
    Optional<ProductVariant> findById(String id);

    @EntityGraph(attributePaths = {"product"})
    Optional<ProductVariant> findBySku(String sku);

    

    List<ProductVariant> findByProductId(String productId);

    boolean existsBySkuAndActiveIsTrue(String sku);
}

