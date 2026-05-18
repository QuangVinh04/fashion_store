package com.fashionstore.clothes_retail_api.modules.product.repository;


import com.fashionstore.clothes_retail_api.modules.product.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, String> {
    @EntityGraph(attributePaths = {"product"})
    Optional<ProductVariant> findById(String id);
}
