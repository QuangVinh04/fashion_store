package com.fashionstore.contracts.cart;

import java.util.List;

public record CartItemsRemovalRequested(String userId, List<String> cartItemIds) {
}
