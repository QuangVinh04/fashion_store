package com.fashionstore.catalog.review.repository;

import com.fashionstore.catalog.review.entity.Review;
import com.fashionstore.catalog.review.entity.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, String>, JpaSpecificationExecutor<Review> {

    interface RatingStats {
        long getTotal();

        Double getAverage();
    }

    interface RatingCount {
        int getRating();

        long getTotal();
    }

    Page<Review> findByProductIdAndStatus(String productId, ReviewStatus status, Pageable pageable);

    Page<Review> findByProductIdAndRatingAndStatus(String productId, Integer rating, ReviewStatus status, Pageable pageable);

    Page<Review> findByUserId(String userId, Pageable pageable);

    Page<Review> findByUserIdAndOrderId(String userId, String orderId, Pageable pageable);

    boolean existsByProductIdAndUserIdAndOrderId(String productId, String userId, String orderId);

    @Query("SELECT COUNT(r) AS total, AVG(r.rating) AS average FROM Review r "
            + "WHERE r.productId = :productId AND r.status = com.fashionstore.catalog.review.entity.ReviewStatus.VISIBLE")
    RatingStats getVisibleStats(@Param("productId") String productId);

    @Query("SELECT r.rating AS rating, COUNT(r) AS total FROM Review r "
            + "WHERE r.productId = :productId AND r.status = com.fashionstore.catalog.review.entity.ReviewStatus.VISIBLE "
            + "GROUP BY r.rating")
    List<RatingCount> countVisibleByRating(@Param("productId") String productId);

    /** Khoá theo sản phẩm tới hết transaction: các lần ghi đánh giá cùng sản phẩm tính lại điểm lần lượt, không ghi đè nhau. */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(:productId))) AS product_rating_lock", nativeQuery = true)
    Integer lockProductRating(@Param("productId") String productId);
}
