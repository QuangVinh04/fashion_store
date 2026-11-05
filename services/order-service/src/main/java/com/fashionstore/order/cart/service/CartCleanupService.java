package com.fashionstore.order.cart.service;

import java.util.Map;

public interface CartCleanupService {

    /** Takes the ordered quantity off the user's active cart; lines fully ordered are removed. No-op without an active cart. */
    void removeOrderedItems(String userId, Map<String, Integer> orderedQuantitiesByVariant);
}
