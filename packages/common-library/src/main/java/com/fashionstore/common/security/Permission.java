package com.fashionstore.common.security;

import java.util.Arrays;
import java.util.Optional;

/** Danh mục quyền backoffice: tên wire, khu vực và nhãn tiếng Việt cho UI. */
public enum Permission {

    DASHBOARD_READ(PermissionNames.DASHBOARD_READ, "Dashboard", "Xem dashboard", true, true),
    REVENUE_READ(PermissionNames.REVENUE_READ, "Dashboard", "Xem doanh thu", true, false),
    ORDER_READ(PermissionNames.ORDER_READ, "Đơn hàng", "Xem đơn hàng", true, true),
    ORDER_MANAGE(PermissionNames.ORDER_MANAGE, "Đơn hàng", "Cập nhật trạng thái, hủy đơn", true, true),
    SHIPMENT_MANAGE(PermissionNames.SHIPMENT_MANAGE, "Vận chuyển", "Quản lý vận đơn", true, true),
    RETURN_READ(PermissionNames.RETURN_READ, "Đổi trả", "Xem yêu cầu đổi trả", true, true),
    RETURN_MANAGE(PermissionNames.RETURN_MANAGE, "Đổi trả", "Xử lý yêu cầu đổi trả", true, true),
    REFUND_APPROVE(PermissionNames.REFUND_APPROVE, "Đổi trả", "Duyệt hoàn tiền", true, false),
    PRODUCT_READ(PermissionNames.PRODUCT_READ, "Sản phẩm", "Xem sản phẩm", true, true),
    PRODUCT_MANAGE(PermissionNames.PRODUCT_MANAGE, "Sản phẩm", "Tạo, sửa, xóa sản phẩm", true, true),
    INVENTORY_READ(PermissionNames.INVENTORY_READ, "Tồn kho", "Xem tồn kho", true, true),
    INVENTORY_MANAGE(PermissionNames.INVENTORY_MANAGE, "Tồn kho", "Điều chỉnh tồn kho", true, true),
    REVIEW_READ(PermissionNames.REVIEW_READ, "Đánh giá", "Xem đánh giá", true, true),
    REVIEW_MANAGE(PermissionNames.REVIEW_MANAGE, "Đánh giá", "Duyệt, ẩn đánh giá", true, true),
    PROMOTION_READ(PermissionNames.PROMOTION_READ, "Khuyến mãi", "Xem khuyến mãi", true, true),
    PROMOTION_MANAGE(PermissionNames.PROMOTION_MANAGE, "Khuyến mãi", "Tạo, sửa khuyến mãi", true, false),
    CUSTOMER_READ(PermissionNames.CUSTOMER_READ, "Khách hàng", "Xem khách hàng", true, true),
    CUSTOMER_MANAGE(PermissionNames.CUSTOMER_MANAGE, "Khách hàng", "Sửa, khóa tài khoản khách hàng", true, false),
    PAYMENT_READ(PermissionNames.PAYMENT_READ, "Thanh toán, ví", "Xem thanh toán", true, true),
    WALLET_READ(PermissionNames.WALLET_READ, "Thanh toán, ví", "Xem ví", true, true),
    WALLET_ADJUST(PermissionNames.WALLET_ADJUST, "Thanh toán, ví", "Nạp, điều chỉnh ví", true, false),
    GROUP_MANAGE(PermissionNames.GROUP_MANAGE, "Phân quyền", "Quản lý nhóm quyền, gán người dùng", false, false);

    private final String authority;
    private final String area;
    private final String label;
    private final boolean isAssignable;
    private final boolean isStaffDefault;

    Permission(String authority, String area, String label, boolean isAssignable, boolean isStaffDefault) {
        this.authority = authority;
        this.area = area;
        this.label = label;
        this.isAssignable = isAssignable;
        this.isStaffDefault = isStaffDefault;
    }

    public String authority() {
        return authority;
    }

    public String area() {
        return area;
    }

    public String label() {
        return label;
    }

    public boolean isAssignable() {
        return isAssignable;
    }

    public boolean isStaffDefault() {
        return isStaffDefault;
    }

    public static String[] authorities() {
        return Arrays.stream(values()).map(Permission::authority).toArray(String[]::new);
    }

    public static Optional<Permission> fromAuthority(String authority) {
        return Arrays.stream(values()).filter(permission -> permission.authority.equals(authority)).findFirst();
    }
}
