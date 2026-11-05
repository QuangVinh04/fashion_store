package com.fashionstore.identity.service;

import com.fashionstore.identity.dto.group.GroupChangeResponse;
import com.fashionstore.identity.dto.group.GroupRequest;
import com.fashionstore.identity.dto.group.GroupResponse;
import com.fashionstore.identity.dto.group.PermissionResponse;
import java.util.List;

public interface GroupService {

    List<PermissionResponse> listPermissions();

    List<GroupResponse> listGroups();

    GroupChangeResponse createGroup(GroupRequest request);

    GroupChangeResponse updateGroup(String groupId, GroupRequest request);

    /** Trả id các thành viên chưa logout được. */
    List<String> deleteGroup(String groupId);

    List<GroupResponse> getUserGroups(String userId);

    /** Chỉ gọi nội bộ (không kiểm quyền): ví dụ thêm nhóm {@code Customer} cho tài khoản vừa tạo. */
    void addToGroupByName(String userId, String groupName);
}
