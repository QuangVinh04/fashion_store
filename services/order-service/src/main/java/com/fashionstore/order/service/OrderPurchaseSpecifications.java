package com.fashionstore.order.service;

import com.fashionstore.order.dto.OrderPurchaseTab;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.OrderItem;
import com.fashionstore.order.entity.ReturnRequest;
import com.fashionstore.order.entity.enumeration.OrderStatus;
import org.springframework.data.jpa.domain.Specification;
import java.util.ArrayList;
import java.util.Locale;
import jakarta.persistence.criteria.Predicate;

public final class OrderPurchaseSpecifications {
    private OrderPurchaseSpecifications() {}

    public static Specification<Order> owned(String userId, OrderStatus status, OrderPurchaseTab tab, String keyword) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("userId"), userId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (tab != null) {
                switch (tab) {
                    case PENDING -> predicates.add(cb.equal(root.get("status"), OrderStatus.PENDING));
                    case PREPARING -> predicates.add(root.get("status").in(OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.PACKED));
                    case SHIPPING -> predicates.add(cb.equal(root.get("status"), OrderStatus.SHIPPING));
                    case COMPLETED -> predicates.add(cb.or(
                            root.get("status").in(OrderStatus.DELIVERED, OrderStatus.COMPLETED),
                            cb.and(cb.equal(root.get("status"), OrderStatus.REFUNDED),
                                    cb.isNotNull(root.get("deliveredAt")))));
                    case CANCELLED -> predicates.add(cb.equal(root.get("status"), OrderStatus.CANCELLED));
                    case RETURNS -> {
                        var returned = query.subquery(String.class);
                        var request = returned.from(ReturnRequest.class);
                        returned.select(request.get("id")).where(cb.equal(request.get("order"), root));
                        predicates.add(cb.exists(returned));
                    }
                }
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT)
                        .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
                var matches = query.subquery(String.class);
                var item = matches.from(OrderItem.class);
                matches.select(item.get("id")).where(cb.equal(item.get("order"), root),
                        cb.like(cb.lower(item.get("productName")), pattern, '!'));
                predicates.add(cb.or(cb.like(cb.lower(root.get("orderCode")), pattern, '!'), cb.exists(matches)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
