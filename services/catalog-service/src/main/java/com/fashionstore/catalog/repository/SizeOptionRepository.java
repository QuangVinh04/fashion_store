package com.fashionstore.catalog.repository;


import com.fashionstore.catalog.entity.option.SizeOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface SizeOptionRepository extends JpaRepository<SizeOption, String> {
    boolean existsByNormalizedName(String normalizedName);
    boolean existsByNormalizedNameAndIdNot(String normalizedName, String id);
    Optional<SizeOption> findByNormalizedName(String normalizedName);
    List<SizeOption> findAllByActiveTrueOrderByDisplayOrderAsc();
}
