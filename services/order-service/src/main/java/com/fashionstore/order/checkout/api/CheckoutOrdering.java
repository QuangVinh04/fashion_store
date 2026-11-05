package com.fashionstore.order.checkout.api;

public interface CheckoutOrdering {

    /** Locks the user's checkout until the surrounding transaction ends. Throws CHECKOUT_NOT_FOUND for another user's or a missing checkout. */
    CheckoutSnapshot lockForOrder(String checkoutId, String userId);

    /** Links the checkout to the order created from it and closes it. */
    void markCompleted(String checkoutId, String orderId);
}
