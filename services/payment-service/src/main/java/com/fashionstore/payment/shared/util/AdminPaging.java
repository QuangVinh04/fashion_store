package com.fashionstore.payment.shared.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/** Page request for admin listings: page >= 0, size clamped to 1..100. */
public final class AdminPaging {

    private AdminPaging() {
    }

    public static Pageable of(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)));
    }
}
