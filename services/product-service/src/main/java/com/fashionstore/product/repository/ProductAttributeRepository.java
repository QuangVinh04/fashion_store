package com.fashionstore.product.repository;

import com.fashionstore.product.model.attribute.ProductAttribute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductAttributeRepository extends JpaRepository<ProductAttribute, String> {
    Optional<ProductAttribute> findByCode(String code);
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, String id);
    Page<ProductAttribute> findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(String name, String code, Pageable pageable);
    List<ProductAttribute> findByPublishedTrueOrderByNameAsc();
}
