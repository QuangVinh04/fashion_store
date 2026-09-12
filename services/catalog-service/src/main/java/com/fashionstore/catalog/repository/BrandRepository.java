package com.fashionstore.catalog.repository;

import com.fashionstore.catalog.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BrandRepository extends JpaRepository<Brand, String> {
    List<Brand> findAllByActiveTrueOrderByNameAsc();
    Optional<Brand> findBySlug(String slug);
    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, String id);
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, String id);
}
