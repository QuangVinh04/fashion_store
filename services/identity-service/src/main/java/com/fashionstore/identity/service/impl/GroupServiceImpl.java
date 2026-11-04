package com.fashionstore.identity.service.impl;

import static com.fashionstore.common.security.KeycloakJwtAuthoritiesConverter.API_CLIENT_ID;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.common.security.Permission;
import com.fashionstore.common.security.PermissionNames;
import com.fashionstore.identity.client.KeycloakAdminClient;
import com.fashionstore.identity.client.KeycloakGroupClient;
import com.fashionstore.identity.dto.group.GroupChangeResponse;
import com.fashionstore.identity.dto.group.GroupMemberResponse;
import com.fashionstore.identity.dto.group.GroupMembersChangeResponse;
import com.fashionstore.identity.dto.group.GroupMembersRequest;
import com.fashionstore.identity.dto.group.GroupRequest;
import com.fashionstore.identity.dto.group.GroupResponse;
import com.fashionstore.identity.dto.group.KeycloakGroupRepresentation;
import com.fashionstore.identity.dto.group.KeycloakUserBrief;
import com.fashionstore.identity.dto.group.MemberChangeFailure;
import com.fashionstore.identity.dto.group.PermissionResponse;
import com.fashionstore.identity.dto.group.UpdateUserGroupsRequest;
import com.fashionstore.identity.dto.group.UserGroupsResponse;
import com.fashionstore.identity.entity.User;
import com.fashionstore.identity.exception.IdentityErrorCode;
import com.fashionstore.identity.repository.UserRepository;
import com.fashionstore.identity.service.CurrentUserProvider;
import com.fashionstore.identity.service.GroupService;
import com.fashionstore.identity.service.SystemGroups;
import feign.FeignException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Không có {@code @Transactional}: dữ liệu nhóm nằm ở Keycloak, identity DB chỉ được đọc. Thay đổi áp dụng xong
 * mới logout thành viên; logout lỗi không hoàn tác thay đổi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private static final String GROUP_MANAGE = "hasAuthority('" + PermissionNames.GROUP_MANAGE + "')";
    private static final int CANDIDATE_SEARCH_LIMIT = 20;
    private static final int CANDIDATE_SEARCH_PAGE_SIZE = 100;
    // Giới hạn số lần gọi Keycloak cho một lần gõ phím: quét tối đa 1000 người khớp từ khóa.
    private static final int CANDIDATE_SEARCH_MAX_PAGES = 10;
    private static final String CUSTOMER_READ = "hasAuthority('" + PermissionNames.CUSTOMER_READ + "')";

    private final KeycloakGroupClient groupClient;
    private final KeycloakAdminClient adminClient;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

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

        if (!name.equals(current.name())) {
            groupClient.renameGroup(groupId, name);
        }
        List<String> memberIds = groupClient.listMemberIds(groupId);
        List<String> notLoggedOut = List.of();
        if (!permissions.equals(permissionsOf(current))) {
            groupClient.setGroupPermissions(groupId, permissions);
            notLoggedOut = logoutAll(memberIds);
        }
        return new GroupChangeResponse(response(groupId, name, permissions, memberIds.size()), notLoggedOut);
    }

    @Override
    @PreAuthorize(GROUP_MANAGE)
    public List<String> deleteGroup(String groupId) {
        requireEditable(groupClient.getGroup(groupId));
        // Lấy danh sách trước: xoá nhóm xong thì không còn biết ai từng là thành viên.
        List<String> memberIds = groupClient.listMemberIds(groupId);
        groupClient.deleteGroup(groupId);
        return logoutAll(memberIds);
    }

    @Override
    @PreAuthorize(GROUP_MANAGE)
    public PageResponse<List<GroupMemberResponse>> listMembers(String groupId, String keyword, Pageable pageable) {
        List<KeycloakUserBrief> members = groupClient.listMembers(groupId);
        if (keyword != null && !keyword.isBlank()) {
            String lowerCaseKeyword = keyword.trim().toLowerCase();
            members = members.stream().filter(member -> member.matches(lowerCaseKeyword)).toList();
        }
        int from = (int) Math.min(pageable.getOffset(), members.size());
        int to = Math.min(from + pageable.getPageSize(), members.size());
        return PageResponse.<List<GroupMemberResponse>>builder()
                .pageNo(pageable.getPageNumber())
                .pageSize(pageable.getPageSize())
                .totalPage((members.size() + pageable.getPageSize() - 1) / pageable.getPageSize())
                .totalElements((long) members.size())
                .items(toMembers(members.subList(from, to)))
                .build();
    }

    @Override
    @PreAuthorize(GROUP_MANAGE)
    public List<GroupMemberResponse> searchCandidates(String groupId, String keyword) {
        requireMembershipEditable(groupId);
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        Set<String> memberIds = Set.copyOf(groupClient.listMemberIds(groupId));
        return toMembers(findNonMembers(keyword.trim(), memberIds));
    }

    // Đọc tiếp trang sau khi cả trang kết quả đã là thành viên, nếu không ô tìm trả rỗng dù còn người thêm được.
    private List<KeycloakUserBrief> findNonMembers(String keyword, Set<String> memberIds) {
        List<KeycloakUserBrief> candidates = new ArrayList<>();
        for (int pageIndex = 0; pageIndex < CANDIDATE_SEARCH_MAX_PAGES; pageIndex++) {
            List<KeycloakUserBrief> page = groupClient.searchUsers(
                    keyword, pageIndex * CANDIDATE_SEARCH_PAGE_SIZE, CANDIDATE_SEARCH_PAGE_SIZE);
            page.stream().filter(user -> !memberIds.contains(user.id())).forEach(candidates::add);
            if (candidates.size() >= CANDIDATE_SEARCH_LIMIT || page.size() < CANDIDATE_SEARCH_PAGE_SIZE) {
                break;
            }
        }
        return candidates.subList(0, Math.min(candidates.size(), CANDIDATE_SEARCH_LIMIT));
    }

    // Hồ sơ trong identity DB (tên người dùng tự sửa, trạng thái khoá) được ưu tiên; người chưa từng đăng nhập lấy từ Keycloak.
    private List<GroupMemberResponse> toMembers(List<KeycloakUserBrief> users) {
        Map<String, User> knownUsers = userRepository.findAllById(users.stream().map(KeycloakUserBrief::id).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return users.stream().map(user -> toMember(user, knownUsers.get(user.id()))).toList();
    }

    @Override
    @PreAuthorize(GROUP_MANAGE)
    public GroupMembersChangeResponse addMembers(String groupId, GroupMembersRequest request) {
        requireMembershipEditable(groupId);
        Set<String> members = new HashSet<>(groupClient.listMemberIds(groupId));
        return changeMembers(request, userId -> {
            if (members.contains(userId)) {
                return false;
            }
            groupClient.addUserToGroup(userId, groupId);
            return true;
        });
    }

    @Override
    @PreAuthorize(GROUP_MANAGE)
    public GroupMembersChangeResponse removeMembers(String groupId, GroupMembersRequest request) {
        boolean isAdminGroup = SystemGroups.ADMIN.equals(requireMembershipEditable(groupId).name());
        Set<String> members = new HashSet<>(groupClient.listMemberIds(groupId));
        return changeMembers(request, userId -> {
            if (!members.contains(userId)) {
                return false;
            }
            if (isAdminGroup) {
                requireAdminMayLeave(userId, members);
            }
            groupClient.removeUserFromGroup(userId, groupId);
            members.remove(userId);
            return true;
        });
    }

    private KeycloakGroupRepresentation requireMembershipEditable(String groupId) {
        KeycloakGroupRepresentation group = groupClient.getGroup(groupId);
        if (SystemGroups.CUSTOMER.equals(group.name())) {
            throw new AppException(IdentityErrorCode.CUSTOMER_GROUP_NOT_ASSIGNABLE);
        }
        return group;
    }

    private void requireAdminMayLeave(String userId, Set<String> remainingAdmins) {
        if (userId.equals(currentUserProvider.getCurrentUserId())) {
            throw new AppException(IdentityErrorCode.CANNOT_LEAVE_OWN_ADMIN);
        }
        if (remainingAdmins.size() <= 1) {
            throw new AppException(IdentityErrorCode.LAST_ADMIN_REQUIRED);
        }
    }

    /**
     * Áp {@code applyChange} cho từng người trong request ({@code true} = có thay đổi thật). Người đã ở đúng trạng thái
     * yêu cầu tính là thành công nhưng không bị đăng xuất.
     */
    private GroupMembersChangeResponse changeMembers(GroupMembersRequest request, Predicate<String> applyChange) {
        List<String> succeeded = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        List<MemberChangeFailure> failures = new ArrayList<>();
        for (String userId : request.userIds()) {
            try {
                if (applyChange.test(userId)) {
                    changed.add(userId);
                }
                succeeded.add(userId);
            } catch (AppException failure) {
                failures.add(failureOf(userId, failure));
            } catch (FeignException rejected) {
                // 4xx chưa ánh xạ (vd. 400, 403) chỉ là lỗi của người này: những người đã đổi vẫn cần được báo và đăng xuất.
                log.warn("[Group] Keycloak answered {} when changing membership of {}", rejected.status(), userId);
                failures.add(failureOf(userId, new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR, rejected)));
            }
        }
        return new GroupMembersChangeResponse(succeeded, failures, logoutAll(changed));
    }

    private static MemberChangeFailure failureOf(String userId, AppException failure) {
        return new MemberChangeFailure(userId, failure.getErrorCode().getCode(), failure.getMessage());
    }

    @Override
    @PreAuthorize(GROUP_MANAGE)
    public UserGroupsResponse updateUserGroups(String userId, UpdateUserGroupsRequest request) {
        // Kiểm tra mọi nhóm trước khi đụng tới membership để lỗi không để lại thay đổi dở dang.
        Map<String, KeycloakGroupRepresentation> requested = new LinkedHashMap<>();
        request.groupIds().forEach(groupId -> requested.put(groupId, groupClient.getGroup(groupId)));
        if (requested.values().stream().anyMatch(group -> SystemGroups.CUSTOMER.equals(group.name()))) {
            throw new AppException(IdentityErrorCode.CUSTOMER_GROUP_NOT_ASSIGNABLE);
        }

        Map<String, KeycloakGroupRepresentation> kept = new LinkedHashMap<>();
        Map<String, KeycloakGroupRepresentation> removed = new LinkedHashMap<>();
        for (KeycloakGroupRepresentation existing : groupClient.listUserGroups(userId)) {
            boolean isKept = requested.containsKey(existing.id()) || SystemGroups.CUSTOMER.equals(existing.name());
            (isKept ? kept : removed).put(existing.id(), existing);
        }
        requireAdminMayBeRemoved(userId, removed.values());

        List<String> added = requested.keySet().stream().filter(id -> !kept.containsKey(id)).toList();
        added.forEach(groupId -> groupClient.addUserToGroup(userId, groupId));
        removed.keySet().forEach(groupId -> groupClient.removeUserFromGroup(userId, groupId));

        boolean isChanged = !added.isEmpty() || !removed.isEmpty();
        List<String> notLoggedOut = isChanged ? logoutAll(List.of(userId)) : List.of();
        // Bản đầy đủ (có quyền) của nhóm được yêu cầu đè lên bản brief lấy từ listUserGroups
        Map<String, KeycloakGroupRepresentation> finalGroups = new LinkedHashMap<>(kept);
        finalGroups.putAll(requested);
        return new UserGroupsResponse(finalGroups.values().stream().map(this::toResponseWithoutCount).toList(), notLoggedOut);
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

    // shortcut: hai request song song cùng gỡ hai admin cuối vẫn lọt qua (Keycloak không có thao tác có điều kiện)
    private void requireAdminMayBeRemoved(String userId, Collection<KeycloakGroupRepresentation> removed) {
        KeycloakGroupRepresentation admin = removed.stream()
                .filter(group -> SystemGroups.ADMIN.equals(group.name()))
                .findFirst()
                .orElse(null);
        if (admin == null) {
            return;
        }
        if (userId.equals(currentUserProvider.getCurrentUserId())) {
            throw new AppException(IdentityErrorCode.CANNOT_LEAVE_OWN_ADMIN);
        }
        if (groupClient.listMemberIds(admin.id()).size() <= 1) {
            throw new AppException(IdentityErrorCode.LAST_ADMIN_REQUIRED);
        }
    }

    private List<String> logoutAll(Collection<String> userIds) {
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

    private GroupResponse toResponse(KeycloakGroupRepresentation group) {
        return response(group.id(), group.name(), permissionsOf(group), groupClient.countMembers(group.id()));
    }

    // Nhóm của một user: người xem chỉ cần tên/quyền, không đọc cả danh sách thành viên (Customer chứa mọi khách hàng).
    private GroupResponse toResponseWithoutCount(KeycloakGroupRepresentation group) {
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

    private static GroupMemberResponse toMember(KeycloakUserBrief keycloakUser, User user) {
        return user == null
                ? new GroupMemberResponse(keycloakUser.id(), keycloakUser.email(), keycloakUser.fullName(),
                        keycloakUser.enabled())
                : new GroupMemberResponse(user.getId(), user.getEmail(), user.getFullName(), user.getIsActive());
    }
}
