package com.fashionstore.product.repository;

import com.fashionstore.product.model.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, String> {
    List<ProductCategory> findByProductId(String productId);
    void deleteByProductId(String productId);
    boolean existsByCategoryId(String categoryId);
}
