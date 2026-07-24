package com.fashionstore.product.repository;

import com.fashionstore.product.model.SizeChartRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SizeChartRowRepository extends JpaRepository<SizeChartRow, String> {
    List<SizeChartRow> findBySizeChartId(String sizeChartId);
}
