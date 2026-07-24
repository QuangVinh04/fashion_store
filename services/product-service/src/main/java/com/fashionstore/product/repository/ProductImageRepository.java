package com.fashionstore.product.repository;

import com.fashionstore.product.model.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, String> {
    List<ProductImage> findByProductIdOrderBySortOrderAsc(String productId);
    boolean existsByProductIdAndColorIsNullAndPrimaryTrue(String productId);

    void deleteByProductId(String productId);
}
