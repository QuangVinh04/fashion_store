package com.fashionstore.catalog.entity;

import com.fashionstore.catalog.entity.enumeration.FitRating;
import com.fashionstore.catalog.entity.enumeration.ReviewStatus;
import com.fashionstore.common.persistence.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@Entity
@Table(name = "review")
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Review extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    Product product;

    @Column(name = "user_id", nullable = false, length = 36)
    String userId;

    @Column(name = "order_id", nullable = false, length = 36)
    String orderId;

    @Column(nullable = false)
    Integer rating;

    @Column(columnDefinition = "TEXT")
    String comment;

    @Column(name = "verified_purchase", nullable = false)
    @Builder.Default
    Boolean verifiedPurchase = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    ReviewStatus status = ReviewStatus.VISIBLE;

    @Column(name = "reply_comment", columnDefinition = "TEXT")
    String replyComment;

    @Column(name = "replied_at")
    LocalDateTime repliedAt;

    @Column(name = "replied_by", length = 36)
    String repliedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "fit_rating", length = 20)
    FitRating fitRating;

    @Column(name = "customer_height")
    Integer customerHeight;

    @Column(name = "customer_weight")
    Integer customerWeight;

    @org.hibernate.annotations.BatchSize(size = 30)
    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    List<ReviewMedia> mediaList = new ArrayList<>();
}
