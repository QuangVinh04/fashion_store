package com.fashionstore.catalog.repository;

import com.fashionstore.catalog.dto.AdminProductFilter;
import com.fashionstore.catalog.entity.Product;
import com.fashionstore.catalog.entity.ProductVariant;
import com.fashionstore.catalog.entity.ProductCategory;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import java.util.ArrayList;
import java.util.Locale;

public final class AdminProductSpecifications {
    private AdminProductSpecifications() {}

    public static Specification<Product> from(AdminProductFilter filter) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
                String pattern = "%" + filter.getKeyword().trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                var variants = query.subquery(Integer.class);
                var variant = variants.from(ProductVariant.class);
                variants.select(cb.literal(1)).where(cb.equal(variant.get("product"), root),
                        cb.like(cb.lower(variant.get("sku")), pattern, '\\'));
                predicates.add(cb.or(cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("slug")), pattern, '\\'), cb.exists(variants)));
            }
            if (filter.getStatus() != null) predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            if (filter.getBrandId() != null && !filter.getBrandId().isBlank()) predicates.add(cb.equal(root.get("brand").get("id"), filter.getBrandId()));
            if (filter.getGender() != null) predicates.add(cb.equal(root.get("gender"), filter.getGender()));
            if (filter.getProductType() != null) predicates.add(cb.equal(root.get("productType"), filter.getProductType()));
            if (filter.getMinPrice() != null) predicates.add(cb.greaterThanOrEqualTo(root.get("basePrice"), filter.getMinPrice()));
            if (filter.getMaxPrice() != null) predicates.add(cb.lessThanOrEqualTo(root.get("basePrice"), filter.getMaxPrice()));
            if (filter.getCategoryId() != null && !filter.getCategoryId().isBlank()) {
                var categories = query.subquery(Integer.class);
                var category = categories.from(ProductCategory.class);
                categories.select(cb.literal(1)).where(cb.equal(category.get("product"), root),
                        cb.equal(category.get("category").get("id"), filter.getCategoryId()));
                predicates.add(cb.exists(categories));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
