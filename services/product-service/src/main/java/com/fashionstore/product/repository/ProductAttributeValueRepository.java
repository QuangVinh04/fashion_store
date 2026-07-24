package com.fashionstore.product.repository;

import com.fashionstore.product.model.attribute.ProductAttributeValue;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductAttributeValueRepository extends JpaRepository<ProductAttributeValue, String> {
    @EntityGraph(attributePaths = {"attribute"})
    List<ProductAttributeValue> findByProductId(String productId);
    void deleteByProductId(String productId);
    List<ProductAttributeValue> findByAttributeIdOrderByPositionAscValueAsc(String attributeId);
    Optional<ProductAttributeValue> findByIdAndAttributeId(String id, String attributeId);
    boolean existsByAttributeIdAndNormalizedValue(String attributeId, String normalizedValue);
    boolean existsByAttributeIdAndNormalizedValueAndIdNot(String attributeId, String normalizedValue, String id);
    void deleteByAttributeId(String attributeId);

    boolean existsByAttributeId(String attributeId);

    boolean existsByAttributeOptionId(String optionId);
}
