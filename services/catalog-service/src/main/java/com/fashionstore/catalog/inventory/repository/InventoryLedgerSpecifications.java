package com.fashionstore.catalog.inventory.repository;

import com.fashionstore.catalog.inventory.entity.InventoryLedger;
import com.fashionstore.catalog.inventory.entity.InventoryLedgerType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class InventoryLedgerSpecifications {

    private InventoryLedgerSpecifications() {
    }

    public static Specification<InventoryLedger> filter(String variantId,
                                                        InventoryLedgerType type,
                                                        LocalDateTime fromTime,
                                                        LocalDateTime toTime) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (variantId != null && !variantId.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("variantId"), variantId));
            }
            if (type != null) {
                predicates.add(criteriaBuilder.equal(root.get("type"), type));
            }
            if (fromTime != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), fromTime));
            }
            if (toTime != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), toTime));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
