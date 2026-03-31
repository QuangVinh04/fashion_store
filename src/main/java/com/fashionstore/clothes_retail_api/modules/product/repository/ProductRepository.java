package com.fashionstore.clothes_retail_api.modules.product.repository;


import com.fashionstore.clothes_retail_api.modules.category.entity.Category;
import com.fashionstore.clothes_retail_api.modules.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, String>, JpaSpecificationExecutor<Product> {
    @EntityGraph(attributePaths = {"category"})
    Page<Product> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"category", "variants"})
    Optional<Product> findDetailProductById(String id);

    @EntityGraph(attributePaths = {"variants"})
    Optional<Product> findProductWithVariantsById(String id);


    @EntityGraph(attributePaths = {"category"})
    Page<Product> findAllByNameContaining(String keyword, Pageable pageable);

    @EntityGraph(attributePaths = {"category"})
    Page<Product> findAllByCategory(Category category, Pageable pageable);
}

