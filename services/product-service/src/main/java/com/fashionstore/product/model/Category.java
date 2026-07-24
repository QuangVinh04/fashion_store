package com.fashionstore.product.model;

import com.fashionstore.common.persistence.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "category")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Category extends BaseEntity {

    @Column(nullable = false, length = 255)
    String name;

    @Column(nullable = false, unique = true, length = 255)
    String slug;

    @Column(columnDefinition = "TEXT")
    String description;

    @Column(name = "thumbnail_media_id", length = 36)
    String thumbnailMediaId;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    Integer displayOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    Category parent;

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    List<ProductCategory> productCategories;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    List<Category> children;

    public String getImageUrl() {
        return thumbnailMediaId;
    }

    public void setImageUrl(String imageUrl) {
        this.thumbnailMediaId = imageUrl;
    }

    public Integer getSortOrder() {
        return displayOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.displayOrder = sortOrder;
    }
}
