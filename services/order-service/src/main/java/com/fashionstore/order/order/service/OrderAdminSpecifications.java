package com.fashionstore.order.order.service;

import com.fashionstore.order.order.dto.AdminOrderFilter;
import com.fashionstore.order.order.entity.Order;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Locale;

public final class OrderAdminSpecifications {
    private static final char LIKE_ESCAPE = '!';

    private OrderAdminSpecifications() {}

    public static Specification<Order> matching(AdminOrderFilter filter) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (filter.userId() != null) {
                predicates.add(cb.equal(root.get("userId"), filter.userId()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.paymentMethod() != null) {
                predicates.add(cb.equal(root.get("paymentMethod"), filter.paymentMethod()));
            }
            if (filter.createdFrom() != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("createdAt"), filter.createdFrom().atStartOfDay()));
            }
            if (filter.createdTo() != null) {
                predicates.add(
                        cb.lessThan(
                                root.get("createdAt"),
                                filter.createdTo().plusDays(1).atStartOfDay()));
            }
            if (filter.search() != null && !filter.search().isBlank()) {
                predicates.add(searchPredicate(root, cb, filter.search()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate searchPredicate(Root<Order> root, CriteriaBuilder cb, String search) {
        String pattern = likePattern(search);
        var address = root.get("address");
        return cb.or(
                cb.like(cb.lower(root.get("orderCode")), pattern, LIKE_ESCAPE),
                cb.like(cb.lower(address.get("recipientName")), pattern, LIKE_ESCAPE),
                cb.like(address.get("recipientPhone"), pattern, LIKE_ESCAPE));
    }

    private static String likePattern(String search) {
        String escaped =
                search.trim()
                        .toLowerCase(Locale.ROOT)
                        .replace("!", "!!")
                        .replace("%", "!%")
                        .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
