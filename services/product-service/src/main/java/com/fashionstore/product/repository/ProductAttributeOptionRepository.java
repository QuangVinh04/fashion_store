package com.fashionstore.product.repository;


import com.fashionstore.product.model.attribute.ProductAttributeOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface ProductAttributeOptionRepository extends JpaRepository<ProductAttributeOption, String> {
    void deleteAllByAttributeId(String attributeId);

    boolean existsByAttributeIdAndNormalizedValue (String attributeId, String normalized);

    Optional<ProductAttributeOption> findByIdAndAttributeId(String optionId, String attributeId);
}
