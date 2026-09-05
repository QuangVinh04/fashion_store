package com.fashionstore.catalog.repository;

import com.fashionstore.catalog.model.ProductImage;
import com.fashionstore.catalog.model.ProductImageVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductImageVariantRepository extends JpaRepository<ProductImageVariant, String> {
    void deleteByVariantIdIn(List<String> variantIds);

}
