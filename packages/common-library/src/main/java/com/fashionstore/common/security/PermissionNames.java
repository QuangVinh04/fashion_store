package com.fashionstore.common.security;

/** Tên quyền dạng hằng số để dùng trong annotation (@PreAuthorize). Mỗi quyền là một client role của `fashion-api`. */
public final class PermissionNames {

    public static final String DASHBOARD_READ = "dashboard:read";
    public static final String REVENUE_READ = "revenue:read";
    public static final String ORDER_READ = "order:read";
    public static final String ORDER_MANAGE = "order:manage";
    public static final String SHIPMENT_MANAGE = "shipment:manage";
    public static final String RETURN_READ = "return:read";
    public static final String RETURN_MANAGE = "return:manage";
    public static final String REFUND_APPROVE = "refund:approve";
    public static final String PRODUCT_READ = "product:read";
    public static final String PRODUCT_MANAGE = "product:manage";
    public static final String INVENTORY_READ = "inventory:read";
    public static final String INVENTORY_MANAGE = "inventory:manage";
    public static final String REVIEW_READ = "review:read";
    public static final String REVIEW_MANAGE = "review:manage";
    public static final String PROMOTION_READ = "promotion:read";
    public static final String PROMOTION_MANAGE = "promotion:manage";
    public static final String CUSTOMER_READ = "customer:read";
    public static final String CUSTOMER_MANAGE = "customer:manage";
    public static final String PAYMENT_READ = "payment:read";
    public static final String WALLET_READ = "wallet:read";
    public static final String WALLET_ADJUST = "wallet:adjust";
    public static final String GROUP_MANAGE = "group:manage";

    private PermissionNames() {
    }
}
