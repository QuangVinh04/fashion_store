package com.fashionstore.clothes_retail_api.modules.auth.repository;

import com.fashionstore.clothes_retail_api.modules.auth.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, String> {
    Optional<Permission> findByName(String name);
    boolean existsByName(String name);
    List<Permission> findAllByNameIn(List<String> names);
}
