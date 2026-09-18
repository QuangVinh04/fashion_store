package com.fashionstore.catalog.repository;

import com.fashionstore.catalog.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, String> {

    Page<Review> findByProductId(String productId, Pageable pageable);

    Page<Review> findByProductIdAndRating(String productId, Integer rating, Pageable pageable);

    boolean existsByProductIdAndUserIdAndOrderId(String productId, String userId, String orderId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.id = :productId")
    Double getAverageRatingByProductId(@Param("productId") String productId);

    long countByProductId(String productId);

    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.product.id = :productId GROUP BY r.rating")
    List<Object[]> countReviewsGroupByRating(@Param("productId") String productId);
}
