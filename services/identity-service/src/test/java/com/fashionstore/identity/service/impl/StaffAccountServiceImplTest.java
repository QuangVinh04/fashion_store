package com.fashionstore.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.common.security.PermissionNames;
import com.fashionstore.identity.client.KeycloakGroupClient;
import com.fashionstore.identity.dto.group.KeycloakGroupRepresentation;
import com.fashionstore.identity.dto.staff.CreateStaffRequest;
import com.fashionstore.identity.dto.staff.KeycloakNewUserRepresentation;
import com.fashionstore.identity.dto.staff.StaffResponse;
import com.fashionstore.identity.exception.IdentityErrorCode;
import com.fashionstore.identity.repository.UserRepository;
import com.fashionstore.identity.service.GroupService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StaffAccountServiceImplTest {

    private static final String NEW_USER_ID = "kc-new";
    private static final String EMAIL = "an@fashion.vn";
    private static final String PASSWORD = "Tam-Mat-Khau-1";

    @Mock
    KeycloakGroupClient groupClient;

    @Mock
    GroupService groupService;

    @Mock
    UserRepository userRepository;

    @Mock
    IdentityAuditRecorder auditRecorder;

    StaffAccountServiceImpl service;

    KeycloakGroupRepresentation customer = group("g-customer", "Customer");
    KeycloakGroupRepresentation sales = group("g-sales", "Bán hàng", PermissionNames.ORDER_READ);
    KeycloakGroupRepresentation stock = group("g-stock", "Kho", PermissionNames.INVENTORY_READ);

    @BeforeEach
    void setUp() {
        service = new StaffAccountServiceImpl(groupClient, groupService, userRepository, auditRecorder);
        for (KeycloakGroupRepresentation each : List.of(customer, sales, stock)) {
            when(groupClient.getGroup(each.id())).thenReturn(each);
            when(groupClient.countMembers(each.id())).thenReturn(1);
        }
        when(groupClient.getGroup("g-missing")).thenThrow(new AppException(IdentityErrorCode.GROUP_NOT_FOUND));
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(groupClient.createUser(any())).thenReturn(NEW_USER_ID);
    }

    private static KeycloakGroupRepresentation group(String id, String name, String... permissions) {
        return new KeycloakGroupRepresentation(id, name, null, Map.of("fashion-api", List.of(permissions)));
    }

    private static CreateStaffRequest request(String... groupIds) {
        return new CreateStaffRequest(EMAIL, "An", "Nguyễn", PASSWORD, Set.of(groupIds));
    }

    private AppException errorOf(CreateStaffRequest request) {
        return catchThrowableOfType(AppException.class, () -> service.createStaff(request));
    }

    @Test
    void createsKeycloakUserWithTemporaryVerifiedCredential() {
        service.createStaff(request("g-sales"));

        ArgumentCaptor<KeycloakNewUserRepresentation> captor =
                ArgumentCaptor.forClass(KeycloakNewUserRepresentation.class);
        verify(groupClient).createUser(captor.capture());
        KeycloakNewUserRepresentation sent = captor.getValue();
        assertThat(sent.username()).isEqualTo(EMAIL);
        assertThat(sent.email()).isEqualTo(EMAIL);
        assertThat(sent.firstName()).isEqualTo("An");
        assertThat(sent.lastName()).isEqualTo("Nguyễn");
        assertThat(sent.enabled()).isTrue();
        assertThat(sent.emailVerified()).isTrue();
        assertThat(sent.credentials()).singleElement().satisfies(credential -> {
            assertThat(credential.value()).isEqualTo(PASSWORD);
            assertThat(credential.temporary()).isTrue();
        });
    }

    @Test
    void addsUserToEveryRequestedGroupAndCustomerThenInsertsUserRow() {
        StaffResponse response = service.createStaff(request("g-sales", "g-stock"));

        verify(groupClient).addUserToGroup(NEW_USER_ID, "g-sales");
        verify(groupClient).addUserToGroup(NEW_USER_ID, "g-stock");
        verify(groupService).addToGroupByName(NEW_USER_ID, "Customer");
        verify(userRepository).insertProvisionedUser(NEW_USER_ID, EMAIL, "An Nguyễn", true);
        assertThat(response.id()).isEqualTo(NEW_USER_ID);
        assertThat(response.email()).isEqualTo(EMAIL);
        assertThat(response.fullName()).isEqualTo("An Nguyễn");
        assertThat(response.groups()).extracting("name").containsExactlyInAnyOrder("Bán hàng", "Kho");
        assertThat(response.groups()).allSatisfy(group -> {
            assertThat(group.permissions()).hasSize(1);
            assertThat(group.memberCount()).isEqualTo(1);
        });
        verify(groupClient, never()).deleteUser(any());
    }

    @Test
    void createStaffRecordsAuditWithoutTheEmail() {
        service.createStaff(request("g-sales"));

        ArgumentCaptor<String> summary = ArgumentCaptor.forClass(String.class);
        verify(auditRecorder).record(eq(IdentityAuditRecorder.STAFF_CREATE), eq("USER"), eq(NEW_USER_ID),
                summary.capture());
        assertThat(summary.getValue()).contains("Bán hàng").doesNotContain(EMAIL);
    }

    @Test
    void failedStaffCreationRecordsNothing() {
        doThrow(new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR))
                .when(groupClient).addUserToGroup(NEW_USER_ID, "g-sales");

        catchThrowable(() -> service.createStaff(request("g-sales")));

        verifyNoInteractions(auditRecorder);
    }

    /** Keycloak lưu email chữ thường; users.email phải khớp để existsByEmail không lọt trùng khác hoa thường. */
    @Test
    void emailIsTrimmedAndLowercasedForEveryConsumer() {
        CreateStaffRequest mixedCase = new CreateStaffRequest(" An@Fashion.VN ", "An", "Nguyễn", PASSWORD, Set.of("g-sales"));

        service.createStaff(mixedCase);

        verify(userRepository).existsByEmail(EMAIL);
        ArgumentCaptor<KeycloakNewUserRepresentation> captor =
                ArgumentCaptor.forClass(KeycloakNewUserRepresentation.class);
        verify(groupClient).createUser(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo(EMAIL);
        verify(userRepository).insertProvisionedUser(NEW_USER_ID, EMAIL, "An Nguyễn", true);
    }

    @Test
    void emailAlreadyInIdentityDatabaseIsRejectedBeforeCallingKeycloak() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        AppException error = errorOf(request("g-sales"));

        assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.ACCOUNT_LINK_CONFLICT);
        verify(groupClient, never()).createUser(any());
    }

    @Test
    void keycloakEmailConflictSurfacesAsStaffEmailTaken() {
        when(groupClient.createUser(any())).thenThrow(new AppException(IdentityErrorCode.STAFF_EMAIL_TAKEN));

        AppException error = errorOf(request("g-sales"));

        assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.STAFF_EMAIL_TAKEN);
        verify(groupClient, never()).deleteUser(any());
    }

    @Test
    void customerGroupIsRejectedBeforeAnythingIsCreated() {
        AppException error = errorOf(request("g-sales", "g-customer"));

        assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.CUSTOMER_GROUP_NOT_ASSIGNABLE);
        verify(groupClient, never()).createUser(any());
        verifyNoInteractions(userRepository, groupService);
    }

    @Test
    void unknownGroupIsRejectedBeforeAnythingIsCreated() {
        AppException error = errorOf(request("g-sales", "g-missing"));

        assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.GROUP_NOT_FOUND);
        verify(groupClient, never()).createUser(any());
        verifyNoInteractions(userRepository, groupService);
    }

    @Test
    void emptyGroupIdsAreRejectedBeforeAnythingIsCreated() {
        AppException error = errorOf(request());

        assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.STAFF_GROUP_REQUIRED);
        verify(groupClient, never()).createUser(any());
        verifyNoInteractions(userRepository, groupService);
    }

    @Test
    void failureWhileAddingToGroupDeletesTheKeycloakUserAndRethrowsOriginal() {
        AppException original = new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR);
        doThrow(original).when(groupClient).addUserToGroup(NEW_USER_ID, "g-sales");

        Throwable thrown = catchThrowable(() -> service.createStaff(request("g-sales")));

        assertThat(thrown).isSameAs(original);
        InOrder order = inOrder(groupClient);
        order.verify(groupClient).createUser(any());
        order.verify(groupClient).deleteUser(NEW_USER_ID);
        verify(userRepository, never()).insertProvisionedUser(any(), any(), any(), anyBoolean());
    }

    @Test
    void failureWhileBuildingResponseDeletesKeycloakUserAndNeverInsertsUserRow() {
        AppException original = new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR);
        when(groupClient.countMembers("g-sales")).thenThrow(original);

        Throwable thrown = catchThrowable(() -> service.createStaff(request("g-sales")));

        assertThat(thrown).isSameAs(original);
        verify(groupClient).deleteUser(NEW_USER_ID);
        verify(userRepository, never()).insertProvisionedUser(any(), any(), any(), anyBoolean());
    }

    @Test
    void failureWhileInsertingUserRowAlsoCompensates() {
        IllegalStateException original = new IllegalStateException("db down");
        doThrow(original).when(userRepository).insertProvisionedUser(NEW_USER_ID, EMAIL, "An Nguyễn", true);

        Throwable thrown = catchThrowable(() -> service.createStaff(request("g-sales")));

        assertThat(thrown).isSameAs(original);
        verify(groupClient).deleteUser(NEW_USER_ID);
    }

    @Test
    void originalFailurePropagatesEvenWhenCompensationFails() {
        AppException original = new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR);
        doThrow(original).when(groupService).addToGroupByName(NEW_USER_ID, "Customer");
        doThrow(new IllegalStateException("keycloak down")).when(groupClient).deleteUser(NEW_USER_ID);

        Throwable thrown = catchThrowable(() -> service.createStaff(request("g-sales")));

        assertThat(thrown).isSameAs(original);
        verify(groupClient).deleteUser(NEW_USER_ID);
    }

    @Test
    void requestToStringDoesNotLeakThePassword() {
        assertThat(request("g-sales").toString()).doesNotContain(PASSWORD).contains(EMAIL);
    }
}
