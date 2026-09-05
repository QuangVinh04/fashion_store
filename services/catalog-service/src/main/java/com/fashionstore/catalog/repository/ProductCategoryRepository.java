package com.fashionstore.catalog.repository;

import com.fashionstore.catalog.model.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, String> {
    List<ProductCategory> findByProductId(String productId);
    void deleteByProductId(String productId);
    boolean existsByCategoryId(String categoryId);
}
