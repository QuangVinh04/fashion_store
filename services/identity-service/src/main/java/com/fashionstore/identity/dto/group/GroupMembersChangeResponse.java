package com.fashionstore.identity.dto.group;

import java.util.List;

/**
 * Kết quả theo từng người: người đã ở đúng trạng thái yêu cầu (đã trong nhóm / không có trong nhóm) vẫn tính là
 * thành công nhưng không bị đăng xuất. {@code notLoggedOutUserIds} là người đã đổi nhưng chưa logout được.
 */
public record GroupMembersChangeResponse(
        List<String> succeededUserIds,
        List<MemberChangeFailure> failures,
        List<String> notLoggedOutUserIds) {
}
