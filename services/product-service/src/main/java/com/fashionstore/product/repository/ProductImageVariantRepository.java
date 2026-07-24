package com.fashionstore.product.repository;

import com.fashionstore.product.model.ProductImage;
import com.fashionstore.product.model.ProductImageVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductImageVariantRepository extends JpaRepository<ProductImageVariant, String> {
    void deleteByVariantIdIn(List<String> variantIds);

}
