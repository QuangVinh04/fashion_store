package com.fashionstore.catalog.repository;


import com.fashionstore.catalog.model.Category;
import com.fashionstore.catalog.model.Product;
import com.fashionstore.catalog.model.enumeration.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, String>, JpaSpecificationExecutor<Product> {
    @EntityGraph(attributePaths = {"brand", "productCategories.category"})
    Page<Product> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"brand", "productCategories.category"})
    Page<Product> findAllByStatus(ProductStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {
            "brand",
            "productCategories.category",
            "variants",
            "variants.colorOption",
            "variants.sizeOption",
            "images",
            "attributeValues.attribute"
    })
    Optional<Product> findDetailProductById(String id);

    @EntityGraph(attributePaths = {"variants", "variants.colorOption", "variants.sizeOption"})
    Optional<Product> findProductWithVariantsById(String id);

    @EntityGraph(attributePaths = {
            "brand",
            "productCategories.category",
            "variants",
            "variants.colorOption",
            "variants.sizeOption",
            "images",
            "attributeValues.attribute"
    })
    Optional<Product> findBySlug(String slug);

    @EntityGraph(attributePaths = {
            "brand",
            "productCategories.category",
            "variants",
            "variants.colorOption",
            "variants.sizeOption",
            "images",
            "attributeValues.attribute"
    })
    Optional<Product> findBySlugAndStatus(String slug, ProductStatus status);

    boolean existsBySlug(String slug);

    @EntityGraph(attributePaths = {"brand", "productCategories.category"})
    Page<Product> findAllByNameContaining(String keyword, Pageable pageable);

    @EntityGraph(attributePaths = {"brand", "productCategories.category"})
    Page<Product> findAllByNameContainingAndStatus(String keyword, ProductStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"brand", "productCategories.category"})
    @Query("select distinct p from Product p join p.productCategories pc where pc.category = :category")
    Page<Product> findAllByCategory(Category category, Pageable pageable);

    @EntityGraph(attributePaths = {"brand", "productCategories.category"})
    @Query("select distinct p from Product p join p.productCategories pc where pc.category.slug = :categorySlug")
    Page<Product> findAllByCategorySlug(String categorySlug, Pageable pageable);

    @EntityGraph(attributePaths = {"brand", "productCategories.category"})
    @Query("select distinct p from Product p join p.productCategories pc where pc.category.slug = :categorySlug and p.status = :status")
    Page<Product> findAllByCategorySlugAndStatus(String categorySlug, ProductStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"brand", "productCategories.category"})
    Page<Product> findAllByBrandId(String brandId, Pageable pageable);

    @EntityGraph(attributePaths = {"brand", "productCategories.category"})
    Page<Product> findAllByBrandSlug(String brandSlug, Pageable pageable);

    @EntityGraph(attributePaths = {"brand", "productCategories.category"})
    Page<Product> findAllByBrandSlugAndStatus(String brandSlug, ProductStatus status, Pageable pageable);

    @Query("select count(pc) > 0 from ProductCategory pc where pc.category.id = :categoryId")
    boolean existsByCategoryId(String categoryId);
}


