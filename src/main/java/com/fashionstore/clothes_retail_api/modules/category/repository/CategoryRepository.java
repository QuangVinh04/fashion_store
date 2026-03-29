package com.fashionstore.clothes_retail_api.modules.category.repository;

import com.fashionstore.clothes_retail_api.modules.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, String> {

}
