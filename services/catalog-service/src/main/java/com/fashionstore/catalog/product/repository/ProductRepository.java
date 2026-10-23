package com.fashionstore.catalog.product.repository;


import com.fashionstore.catalog.product.entity.Category;
import com.fashionstore.catalog.product.entity.Product;
import com.fashionstore.catalog.product.entity.ProductStatus;
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
    interface CategoryProductCount {
        String getCategoryId();
        Long getProductCount();
    }

    @Query("select pc.category.id as categoryId, count(distinct pc.product.id) as productCount from ProductCategory pc group by pc.category.id")
    java.util.List<CategoryProductCount> countDirectProductsByCategory();
    @EntityGraph(attributePaths = {"brand", "productCategories.category"})
    Page<Product> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"brand", "productCategories.category"})
    Page<Product> findAllByStatus(ProductStatus status, Pageable pageable);

    // Fetch one collection here; remaining collections load within the service transaction.
    // Joining multiple List associations triggers Hibernate MultipleBagFetchException.
    @EntityGraph(attributePaths = {"brand", "productCategories.category"})
    Optional<Product> findDetailProductById(String id);

    @EntityGraph(attributePaths = {"brand", "productCategories.category"})
    Optional<Product> findBySlug(String slug);

    @EntityGraph(attributePaths = {"brand", "productCategories.category"})
    Optional<Product> findBySlugAndStatus(String slug, ProductStatus status);

    boolean existsBySlug(String slug);

    @EntityGraph(attributePaths = {"brand", "productCategories.category"})
    Page<Product> findAllByNameContainingAndStatus(String keyword, ProductStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"brand", "productCategories.category"})
    @Query("select distinct p from Product p join p.productCategories pc where pc.category.slug = :categorySlug and p.status = :status")
    Page<Product> findAllByCategorySlugAndStatus(String categorySlug, ProductStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"brand", "productCategories.category"})
    Page<Product> findAllByBrandSlugAndStatus(String brandSlug, ProductStatus status, Pageable pageable);

    @Query("select count(pc) > 0 from ProductCategory pc where pc.category.id = :categoryId")
    boolean existsByCategoryId(String categoryId);
}


