package com.fashionstore.order.promotion.repository;

import com.fashionstore.order.promotion.entity.Promotion;
import com.fashionstore.order.promotion.dto.PromotionTarget;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, String> {

    Optional<Promotion> findByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Promotion p where lower(p.code) = lower(:code)")
    Optional<Promotion> findForUpdateByCodeIgnoreCase(@Param("code") String code);

    boolean existsByCodeIgnoreCase(String code);

    @Query("select p from Promotion p where (:active is null or p.active = :active) "
            + "and (:target is null or p.target = :target)")
    Page<Promotion> search(
            @Param("active") Boolean active, @Param("target") PromotionTarget target, Pageable pageable);

    @Query("select p from Promotion p where p.active = true and p.isPublic = true "
            + "and p.startAt <= :now and p.endAt >= :now and (:target is null or p.target = :target) "
            + "order by p.endAt asc")
    List<Promotion> findPublicCurrentlyValid(
            @Param("now") LocalDateTime now, @Param("target") PromotionTarget target);
}
