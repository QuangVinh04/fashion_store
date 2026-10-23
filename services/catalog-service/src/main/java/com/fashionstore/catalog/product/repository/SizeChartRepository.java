package com.fashionstore.catalog.product.repository;

import com.fashionstore.catalog.product.entity.SizeChart;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SizeChartRepository extends JpaRepository<SizeChart, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select chart from SizeChart chart where chart.id = :id")
    Optional<SizeChart> findLockedById(String id);
    @EntityGraph(attributePaths = {"rows"})
    Optional<SizeChart> findDetailById(String id);

    List<SizeChart> findAllByActiveTrueOrderByNameAsc();
}
