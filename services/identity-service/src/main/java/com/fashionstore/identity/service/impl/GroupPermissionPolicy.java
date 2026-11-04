package com.fashionstore.identity.service.impl;

import static com.fashionstore.common.security.PermissionNames.CUSTOMER_MANAGE;
import static com.fashionstore.common.security.PermissionNames.CUSTOMER_READ;
import static com.fashionstore.common.security.PermissionNames.DASHBOARD_READ;
import static com.fashionstore.common.security.PermissionNames.INVENTORY_MANAGE;
import static com.fashionstore.common.security.PermissionNames.INVENTORY_READ;
import static com.fashionstore.common.security.PermissionNames.ORDER_MANAGE;
import static com.fashionstore.common.security.PermissionNames.ORDER_READ;
import static com.fashionstore.common.security.PermissionNames.PRODUCT_MANAGE;
import static com.fashionstore.common.security.PermissionNames.PRODUCT_READ;
import static com.fashionstore.common.security.PermissionNames.PROMOTION_MANAGE;
import static com.fashionstore.common.security.PermissionNames.PROMOTION_READ;
import static com.fashionstore.common.security.PermissionNames.REFUND_APPROVE;
import static com.fashionstore.common.security.PermissionNames.RETURN_MANAGE;
import static com.fashionstore.common.security.PermissionNames.RETURN_READ;
import static com.fashionstore.common.security.PermissionNames.REVENUE_READ;
import static com.fashionstore.common.security.PermissionNames.REVIEW_MANAGE;
import static com.fashionstore.common.security.PermissionNames.REVIEW_READ;
import static com.fashionstore.common.security.PermissionNames.SHIPMENT_MANAGE;
import static com.fashionstore.common.security.PermissionNames.WALLET_ADJUST;
import static com.fashionstore.common.security.PermissionNames.WALLET_READ;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.security.Permission;
import com.fashionstore.identity.exception.IdentityErrorCode;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Kiểm tra quyền được phép gán cho nhóm tự tạo và bổ sung quyền đọc mà quyền ghi kéo theo. */
final class GroupPermissionPolicy {

    // Service gọi method READ lồng trong luồng MANAGE: thiếu READ thì thao tác bị 403 sau khi đã commit.
    private static final Map<String, String> IMPLIED_READ = Map.ofEntries(
            Map.entry(ORDER_MANAGE, ORDER_READ),
            Map.entry(SHIPMENT_MANAGE, ORDER_READ),
            Map.entry(RETURN_MANAGE, RETURN_READ),
            Map.entry(REFUND_APPROVE, RETURN_READ),
            Map.entry(PRODUCT_MANAGE, PRODUCT_READ),
            Map.entry(INVENTORY_MANAGE, INVENTORY_READ),
            Map.entry(REVIEW_MANAGE, REVIEW_READ),
            Map.entry(PROMOTION_MANAGE, PROMOTION_READ),
            Map.entry(CUSTOMER_MANAGE, CUSTOMER_READ),
            Map.entry(WALLET_ADJUST, WALLET_READ),
            Map.entry(REVENUE_READ, DASHBOARD_READ));

    private GroupPermissionPolicy() {
    }

    /** Kiểm tra trên tập được yêu cầu rồi mới thêm quyền kéo theo; trả tập đã chuẩn hoá. */
    static Set<String> normalize(Set<String> requested) {
        boolean isAllAssignable = requested.stream().allMatch(
                authority -> Permission.fromAuthority(authority).filter(Permission::isAssignable).isPresent());
        if (!isAllAssignable) {
            throw new AppException(IdentityErrorCode.PERMISSION_NOT_ASSIGNABLE);
        }
        Set<String> normalized = new HashSet<>(requested);
        for (String authority : requested) {
            String implied = IMPLIED_READ.get(authority);
            if (implied != null) {
                normalized.add(implied);
            }
        }
        return normalized;
    }
}
