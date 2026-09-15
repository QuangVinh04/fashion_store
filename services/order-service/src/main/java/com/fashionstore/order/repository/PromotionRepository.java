package com.fashionstore.order.repository;

import com.fashionstore.order.entity.Promotion;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, String> {

    Optional<Promotion> findByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Promotion p where lower(p.code) = lower(:code)")
    Optional<Promotion> findForUpdateByCodeIgnoreCase(@Param("code") String code);

    boolean existsByCodeIgnoreCase(String code);

    Page<Promotion> findByActive(Boolean active, Pageable pageable);
}
