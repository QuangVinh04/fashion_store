package com.fashionstore.identity.service.impl;

import static com.fashionstore.common.security.KeycloakJwtAuthoritiesConverter.API_CLIENT_ID;
import static com.fashionstore.identity.service.impl.PreAuthorizeExpressions.CUSTOMER_READ;
import static com.fashionstore.identity.service.impl.PreAuthorizeExpressions.GROUP_MANAGE;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.security.Permission;
import com.fashionstore.identity.client.KeycloakAdminClient;
import com.fashionstore.identity.client.KeycloakGroupClient;
import com.fashionstore.identity.dto.group.GroupChangeResponse;
import com.fashionstore.identity.dto.group.GroupRequest;
import com.fashionstore.identity.dto.group.GroupResponse;
import com.fashionstore.identity.dto.group.KeycloakGroupRepresentation;
import com.fashionstore.identity.dto.group.PermissionResponse;
import com.fashionstore.identity.exception.IdentityErrorCode;
import com.fashionstore.identity.service.GroupService;
import com.fashionstore.identity.service.SystemGroups;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Không có {@code @Transactional}: dữ liệu nhóm nằm ở Keycloak. Thay đổi áp dụng xong mới logout thành viên;
 * logout lỗi không hoàn tác thay đổi. Thành viên của nhóm do {@link GroupMembershipServiceImpl} xử lý.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private static final String GROUP_ENTITY = "GROUP";

    private final KeycloakGroupClient groupClient;
    private final KeycloakAdminClient adminClient;
    private final IdentityAuditRecorder auditRecorder;

    @Override
    @PreAuthorize(GROUP_MANAGE)
    public List<PermissionResponse> listPermissions() {
        return Arrays.stream(Permission.values())
                .map(permission -> new PermissionResponse(
                        permission.authority(), permission.area(), permission.label(), permission.isAssignable()))
                .toList();
    }

    @Override
    @PreAuthorize(GROUP_MANAGE)
    public List<GroupResponse> listGroups() {
        return groupClient.listGroups().stream().map(this::toResponse).toList();
    }

    @Override
    @PreAuthorize(GROUP_MANAGE)
    public GroupChangeResponse createGroup(GroupRequest request) {
        Set<String> permissions = GroupPermissionPolicy.normalize(request.permissions());
        String name = request.name().trim();
        requireNameAvailable(name, null);

        String groupId = groupClient.createGroup(name);
        try {
            groupClient.setGroupPermissions(groupId, permissions);
        } catch (RuntimeException permissionFailure) {
            // Không để lại nhóm rỗng giữ mất tên vừa đặt; bắt cả FeignException 4xx (UpstreamFailure trả nguyên)
            deleteQuietly(groupId, permissionFailure);
            throw permissionFailure;
        }
        auditRecorder.record(IdentityAuditRecorder.GROUP_CREATE, GROUP_ENTITY, groupId,
                "Tạo nhóm " + name + " (quyền: " + describe(permissions) + ")");
        return new GroupChangeResponse(response(groupId, name, permissions, 0), List.of());
    }

    @Override
    @PreAuthorize(GROUP_MANAGE)
    public GroupChangeResponse updateGroup(String groupId, GroupRequest request) {
        KeycloakGroupRepresentation current = groupClient.getGroup(groupId);
        requireEditable(current);
        Set<String> permissions = GroupPermissionPolicy.normalize(request.permissions());
        String name = request.name().trim();
        requireNameAvailable(name, groupId);

        boolean isRenamed = !name.equals(current.name());
        boolean isPermissionChanged = !permissions.equals(permissionsOf(current));
        if (isRenamed) {
            groupClient.renameGroup(groupId, name);
        }
        List<String> memberIds = groupClient.listMemberIds(groupId);
        List<String> notLoggedOut = List.of();
        if (isPermissionChanged) {
            groupClient.setGroupPermissions(groupId, permissions);
            notLoggedOut = MemberLogout.logoutAll(adminClient, memberIds);
        }
        if (isRenamed || isPermissionChanged) {
            auditRecorder.record(IdentityAuditRecorder.GROUP_UPDATE, GROUP_ENTITY, groupId,
                    "Sửa nhóm " + name + " (quyền: " + describe(permissions) + ")");
        }
        return new GroupChangeResponse(response(groupId, name, permissions, memberIds.size()), notLoggedOut);
    }

    @Override
    @PreAuthorize(GROUP_MANAGE)
    public List<String> deleteGroup(String groupId) {
        KeycloakGroupRepresentation group = groupClient.getGroup(groupId);
        requireEditable(group);
        // Lấy danh sách trước: xoá nhóm xong thì không còn biết ai từng là thành viên.
        List<String> memberIds = groupClient.listMemberIds(groupId);
        groupClient.deleteGroup(groupId);
        auditRecorder.record(IdentityAuditRecorder.GROUP_DELETE, GROUP_ENTITY, groupId, "Xoá nhóm " + group.name());
        return MemberLogout.logoutAll(adminClient, memberIds);
    }

    private static String describe(Set<String> permissions) {
        return String.join(", ", new TreeSet<>(permissions));
    }

    @Override
    @PreAuthorize(CUSTOMER_READ)
    public List<GroupResponse> getUserGroups(String userId) {
        // Danh sách nhóm của user ở dạng brief (không có quyền) nên lấy lại bản đầy đủ theo id.
        return groupClient.listUserGroups(userId).stream()
                .map(group -> toResponseWithoutCount(groupClient.getGroup(group.id())))
                .toList();
    }

    @Override
    public void addToGroupByName(String userId, String groupName) {
        KeycloakGroupRepresentation group = groupClient.listGroups().stream()
                .filter(each -> groupName.equals(each.name()))
                .findFirst()
                .orElseThrow(() -> new AppException(IdentityErrorCode.GROUP_NOT_FOUND));
        groupClient.addUserToGroup(userId, group.id());
    }

    private void deleteQuietly(String groupId, RuntimeException cause) {
        try {
            groupClient.deleteGroup(groupId);
        } catch (RuntimeException cleanupFailure) {
            log.warn("[Group] could not remove half-created group {}", groupId, cleanupFailure);
            cause.addSuppressed(cleanupFailure);
        }
    }

    private void requireEditable(KeycloakGroupRepresentation group) {
        if (SystemGroups.isSystem(group.name())) {
            throw new AppException(IdentityErrorCode.SYSTEM_GROUP_READ_ONLY);
        }
    }

    /** Tên trùng không phân biệt hoa thường với nhóm khác (bỏ qua chính nhóm đang sửa) hoặc trùng nhóm hệ thống. */
    private void requireNameAvailable(String name, String ownGroupId) {
        boolean isTaken = SystemGroups.isReserved(name) || groupClient.listGroups().stream()
                .anyMatch(group -> !group.id().equals(ownGroupId) && group.name().equalsIgnoreCase(name));
        if (isTaken) {
            throw new AppException(IdentityErrorCode.GROUP_NAME_TAKEN);
        }
    }

    private GroupResponse toResponse(KeycloakGroupRepresentation group) {
        return response(group.id(), group.name(), permissionsOf(group), groupClient.countMembers(group.id()));
    }

    // Nhóm của một user: người xem chỉ cần tên/quyền, không đọc cả danh sách thành viên (Customer chứa mọi khách hàng).
    static GroupResponse toResponseWithoutCount(KeycloakGroupRepresentation group) {
        return response(group.id(), group.name(), permissionsOf(group), null);
    }

    static GroupResponse response(String id, String name, Set<String> permissions, Integer memberCount) {
        boolean isSystem = SystemGroups.isSystem(name);
        return new GroupResponse(id, name, permissions.stream().sorted().toList(), memberCount, isSystem, !isSystem);
    }

    static Set<String> permissionsOf(KeycloakGroupRepresentation group) {
        if (group.clientRoles() == null) {
            return Set.of();
        }
        return Set.copyOf(group.clientRoles().getOrDefault(API_CLIENT_ID, List.of()));
    }
}
