package com.fashionstore.product.repository;


import com.fashionstore.product.model.attribute.ProductAttributeOption;
import com.fashionstore.product.model.option.ColorOption;
import com.fashionstore.product.model.option.SizeOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface SizeOptionRepository extends JpaRepository<SizeOption, String> {
    boolean existsByNormalizedName(String normalizedName);
    Optional<SizeOption> findByNormalizedName(String normalizedName);
    List<SizeOption> findAllByActiveTrueOrderByDisplayOrderAsc();
}
