package com.fashionstore.catalog.repository;


import com.fashionstore.catalog.entity.attribute.ProductAttributeOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;


@Repository
public interface ProductAttributeOptionRepository extends JpaRepository<ProductAttributeOption, String> {
    List<ProductAttributeOption> findByAttributeIdOrderByValueAsc(String attributeId);

    boolean existsByAttributeIdAndNormalizedValueAndIdNot(String attributeId, String normalizedValue, String id);
    void deleteAllByAttributeId(String attributeId);

    boolean existsByAttributeIdAndNormalizedValue (String attributeId, String normalized);

    Optional<ProductAttributeOption> findByIdAndAttributeId(String optionId, String attributeId);
}
