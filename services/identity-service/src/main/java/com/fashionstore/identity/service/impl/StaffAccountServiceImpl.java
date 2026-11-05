package com.fashionstore.identity.service.impl;

import static com.fashionstore.identity.service.impl.PreAuthorizeExpressions.GROUP_MANAGE;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.identity.client.KeycloakGroupClient;
import com.fashionstore.identity.dto.group.GroupResponse;
import com.fashionstore.identity.dto.group.KeycloakGroupRepresentation;
import com.fashionstore.identity.dto.staff.CreateStaffRequest;
import com.fashionstore.identity.dto.staff.KeycloakNewUserRepresentation;
import com.fashionstore.identity.dto.staff.StaffResponse;
import com.fashionstore.identity.exception.IdentityErrorCode;
import com.fashionstore.identity.repository.UserRepository;
import com.fashionstore.identity.service.GroupService;
import com.fashionstore.identity.service.StaffAccountService;
import com.fashionstore.identity.service.SystemGroups;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Không có {@code @Transactional}: user nằm ở Keycloak nên lỗi sau khi tạo được bù bằng {@code deleteUser}
 * thay vì rollback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaffAccountServiceImpl implements StaffAccountService {

    private static final String FULL_NAME_SEPARATOR = " ";

    private final KeycloakGroupClient groupClient;
    private final GroupService groupService;
    private final UserRepository userRepository;

    @Override
    @PreAuthorize(GROUP_MANAGE)
    public StaffResponse createStaff(CreateStaffRequest request) {
        // Kiểm tra mọi nhóm trước khi tạo gì để lỗi không để lại user mồ côi ở Keycloak.
        List<KeycloakGroupRepresentation> groups = loadAssignableGroups(request);
        // Keycloak lưu email chữ thường; users.email phải cùng dạng để kiểm tra trùng không bị lọt.
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new AppException(IdentityErrorCode.ACCOUNT_LINK_CONFLICT);
        }

        String fullName = request.firstName() + FULL_NAME_SEPARATOR + request.lastName();
        String userId = groupClient.createUser(KeycloakNewUserRepresentation.withTemporaryPassword(
                email, request.firstName(), request.lastName(), request.temporaryPassword()));
        try {
            groups.forEach(group -> groupClient.addUserToGroup(userId, group.id()));
            groupService.addToGroupByName(userId, SystemGroups.CUSTOMER);
            StaffResponse response = new StaffResponse(userId, email, fullName, describe(groups));
            // Bước cuối có thể lỗi: insert commit ngay nên không được đứng trước bước nào còn có thể ném lỗi.
            userRepository.insertProvisionedUser(userId, email, fullName, true);
            return response;
        } catch (RuntimeException failure) {
            // Cố ý bắt rộng: mọi lỗi sau khi tạo user đều phải dọn user, rồi ném lại đúng lỗi gốc.
            deleteOrphan(userId);
            throw failure;
        }
    }

    private List<KeycloakGroupRepresentation> loadAssignableGroups(CreateStaffRequest request) {
        if (request.groupIds() == null || request.groupIds().isEmpty()) {
            throw new AppException(IdentityErrorCode.STAFF_GROUP_REQUIRED);
        }
        List<KeycloakGroupRepresentation> groups = request.groupIds().stream().map(groupClient::getGroup).toList();
        groups.forEach(GroupPermissionPolicy::requireAssignable);
        return groups;
    }

    // Dựng từ nhóm đã tải ở bước kiểm tra: getUserGroups đòi customer:read mà người gọi có thể không có.
    private List<GroupResponse> describe(List<KeycloakGroupRepresentation> groups) {
        return groups.stream()
                .map(group -> GroupServiceImpl.response(group.id(), group.name(),
                        GroupServiceImpl.permissionsOf(group), groupClient.countMembers(group.id())))
                .toList();
    }

    private void deleteOrphan(String userId) {
        try {
            groupClient.deleteUser(userId);
        } catch (RuntimeException cleanupFailure) {
            log.error("Orphan Keycloak user {} after failed staff creation, clean up manually", userId, cleanupFailure);
        }
    }
}
