package com.fashionstore.catalog.repository;


import com.fashionstore.catalog.entity.option.ColorOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface ColorOptionRepository extends JpaRepository<ColorOption, String> {
    boolean existsByNormalizedName(String normalizedName);
    boolean existsByNormalizedNameAndIdNot(String normalizedName, String id);
    Optional<ColorOption> findByNormalizedName(String normalizedName);
    List<ColorOption> findAllByActiveTrueOrderByDisplayOrderAsc();
}
