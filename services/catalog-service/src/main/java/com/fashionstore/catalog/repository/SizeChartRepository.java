package com.fashionstore.catalog.repository;

import com.fashionstore.catalog.model.SizeChart;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SizeChartRepository extends JpaRepository<SizeChart, String> {
    @EntityGraph(attributePaths = {"rows"})
    Optional<SizeChart> findDetailById(String id);
}
