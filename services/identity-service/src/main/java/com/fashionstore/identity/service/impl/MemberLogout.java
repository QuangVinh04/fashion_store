package com.fashionstore.identity.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.identity.client.KeycloakAdminClient;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class MemberLogout {

    private MemberLogout() {
    }

    /** Trả id những người chưa đăng xuất được; lỗi một người không chặn những người còn lại. */
    static List<String> logoutAll(KeycloakAdminClient adminClient, Collection<String> userIds) {
        List<String> notLoggedOut = new ArrayList<>();
        for (String userId : userIds) {
            try {
                adminClient.logout(userId);
            } catch (AppException logoutFailure) {
                // Tiếp tục được: thay đổi đã áp dụng, access token cũ hết hạn sau 5 phút và refresh mang quyền mới.
                log.warn("[Group] could not log out user {}: {}", userId, logoutFailure.getMessage());
                notLoggedOut.add(userId);
            }
        }
        return notLoggedOut;
    }
}
