package com.fashionstore.identity.service;

/** Nhóm hệ thống nhận diện theo tên (khớp realm file / script cài đặt Keycloak). */
public final class SystemGroups {

    public static final String ADMIN = "Admin";
    public static final String CUSTOMER = "Customer";

    private SystemGroups() {
    }

    public static boolean isSystem(String groupName) {
        return ADMIN.equals(groupName) || CUSTOMER.equals(groupName);
    }

    public static boolean isReserved(String candidateName) {
        return ADMIN.equalsIgnoreCase(candidateName) || CUSTOMER.equalsIgnoreCase(candidateName);
    }
}
