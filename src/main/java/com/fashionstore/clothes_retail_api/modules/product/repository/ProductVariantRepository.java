package com.fashionstore.clothes_retail_api.modules.product.repository;


import com.fashionstore.clothes_retail_api.modules.product.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, String> {
}

