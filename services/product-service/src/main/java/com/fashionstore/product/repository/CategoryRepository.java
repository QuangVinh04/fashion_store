package com.fashionstore.product.repository;

import com.fashionstore.product.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, String> {


    @Query("SELECT EXISTS(SELECT 1 FROM Category WHERE name = :name) AS is_exists")
    boolean existsCategoryByName(@Param("name") String name);

    boolean existsByNameAndIdNot(String name, String id);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, String id);

    Optional<Category> findBySlug(String slug);

    boolean existsByParentId(String parentId);

    List<Category> findByParentIsNullOrderByNameAsc();
}

