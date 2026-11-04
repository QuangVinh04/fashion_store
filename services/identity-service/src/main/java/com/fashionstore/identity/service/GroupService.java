package com.fashionstore.identity.service;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.identity.dto.group.GroupChangeResponse;
import com.fashionstore.identity.dto.group.GroupMemberResponse;
import com.fashionstore.identity.dto.group.GroupMembersChangeResponse;
import com.fashionstore.identity.dto.group.GroupMembersRequest;
import com.fashionstore.identity.dto.group.GroupRequest;
import com.fashionstore.identity.dto.group.GroupResponse;
import com.fashionstore.identity.dto.group.PermissionResponse;
import com.fashionstore.identity.dto.group.UpdateUserGroupsRequest;
import com.fashionstore.identity.dto.group.UserGroupsResponse;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface GroupService {

    List<PermissionResponse> listPermissions();

    List<GroupResponse> listGroups();

    GroupChangeResponse createGroup(GroupRequest request);

    GroupChangeResponse updateGroup(String groupId, GroupRequest request);

    /** Trả id các thành viên chưa logout được. */
    List<String> deleteGroup(String groupId);

    /** {@code keyword} (tuỳ chọn) khớp một phần email / username / họ tên, không phân biệt hoa thường. */
    PageResponse<List<GroupMemberResponse>> listMembers(String groupId, String keyword, Pageable pageable);

    /** Tối đa 20 người khớp {@code keyword} trong Keycloak và chưa thuộc nhóm (để chọn khi thêm thành viên). */
    List<GroupMemberResponse> searchCandidates(String groupId, String keyword);

    /** Thêm nhiều người vào một nhóm; lỗi từng người không chặn những người còn lại. */
    GroupMembersChangeResponse addMembers(String groupId, GroupMembersRequest request);

    /** Gỡ nhiều người khỏi một nhóm; lỗi từng người không chặn những người còn lại. */
    GroupMembersChangeResponse removeMembers(String groupId, GroupMembersRequest request);

    UserGroupsResponse updateUserGroups(String userId, UpdateUserGroupsRequest request);

    List<GroupResponse> getUserGroups(String userId);

    /** Chỉ gọi nội bộ (không kiểm quyền): ví dụ thêm nhóm {@code Customer} cho tài khoản vừa tạo. */
    void addToGroupByName(String userId, String groupName);
}
