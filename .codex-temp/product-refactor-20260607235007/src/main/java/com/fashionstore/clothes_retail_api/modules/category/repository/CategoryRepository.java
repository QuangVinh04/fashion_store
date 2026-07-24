package com.fashionstore.clothes_retail_api.modules.category.repository;

import com.fashionstore.clothes_retail_api.modules.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface CategoryRepository extends JpaRepository<Category, String> {


    @Query("SELECT EXISTS(SELECT 1 FROM Category WHERE name = :name) AS is_exists")
    boolean existsCategoryByName(@Param("name") String name);
}
