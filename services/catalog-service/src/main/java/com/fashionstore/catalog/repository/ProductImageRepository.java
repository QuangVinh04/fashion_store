package com.fashionstore.catalog.repository;

import com.fashionstore.catalog.model.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, String> {
    List<ProductImage> findByProductIdOrderBySortOrderAsc(String productId);
    boolean existsByProductIdAndColorIsNullAndIsPrimaryTrue(String productId);

    void deleteByProductId(String productId);
}
