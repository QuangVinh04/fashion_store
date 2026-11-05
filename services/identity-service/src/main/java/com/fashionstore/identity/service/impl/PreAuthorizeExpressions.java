package com.fashionstore.identity.service.impl;

import com.fashionstore.common.security.PermissionNames;

final class PreAuthorizeExpressions {

    static final String GROUP_MANAGE = "hasAuthority('" + PermissionNames.GROUP_MANAGE + "')";
    static final String CUSTOMER_READ = "hasAuthority('" + PermissionNames.CUSTOMER_READ + "')";
    static final String CUSTOMER_MANAGE = "hasAuthority('" + PermissionNames.CUSTOMER_MANAGE + "')";

    private PreAuthorizeExpressions() {
    }
}
