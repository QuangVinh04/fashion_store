package com.fashionstore.catalog.review.repository;

import com.fashionstore.catalog.review.entity.Review;
import com.fashionstore.catalog.review.entity.ReviewStatus;
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

    Page<Review> findByProductIdAndStatus(String productId, ReviewStatus status, Pageable pageable);

    Page<Review> findByProductIdAndRatingAndStatus(String productId, Integer rating, ReviewStatus status, Pageable pageable);

    boolean existsByProductIdAndUserIdAndOrderId(String productId, String userId, String orderId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.id = :productId AND r.status = com.fashionstore.catalog.review.entity.ReviewStatus.VISIBLE")
    Double getAverageRatingByProductId(@Param("productId") String productId);

    long countByProductId(String productId);

    long countByProductIdAndStatus(String productId, ReviewStatus status);

    @Query(value = "SELECT r FROM Review r JOIN FETCH r.product WHERE " +
           "(:rating IS NULL OR r.rating = :rating) AND " +
           "(:status IS NULL OR r.status = :status) AND " +
           "(:keyword IS NULL OR :keyword = '' OR LOWER(r.product.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(r.comment) LIKE LOWER(CONCAT('%', :keyword, '%')))",
           countQuery = "SELECT COUNT(r) FROM Review r WHERE " +
           "(:rating IS NULL OR r.rating = :rating) AND " +
           "(:status IS NULL OR r.status = :status) AND " +
           "(:keyword IS NULL OR :keyword = '' OR LOWER(r.product.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(r.comment) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Review> findAllForAdmin(
            @Param("keyword") String keyword,
            @Param("rating") Integer rating,
            @Param("status") ReviewStatus status,
            Pageable pageable
    );

    default Page<Review> findAllForAdmin(Integer rating, Pageable pageable) {
        return findAllForAdmin(null, rating, null, pageable);
    }

    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.product.id = :productId AND r.status = com.fashionstore.catalog.review.entity.ReviewStatus.VISIBLE GROUP BY r.rating")
    List<Object[]> countReviewsGroupByRating(@Param("productId") String productId);
}
