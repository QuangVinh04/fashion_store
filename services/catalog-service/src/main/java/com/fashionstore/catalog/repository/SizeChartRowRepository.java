package com.fashionstore.catalog.repository;

import com.fashionstore.catalog.model.SizeChartRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SizeChartRowRepository extends JpaRepository<SizeChartRow, String> {
    List<SizeChartRow> findBySizeChartId(String sizeChartId);
}
