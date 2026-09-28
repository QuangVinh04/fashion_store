package com.fashionstore.identity.service.impl;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.identity.client.KeycloakAdminClient;
import com.fashionstore.identity.dto.user.AdminUpdateUserRequest;
import com.fashionstore.identity.dto.user.AdminUserDetailResponse;
import com.fashionstore.identity.dto.user.AdminUserResponse;
import com.fashionstore.identity.entity.User;
import com.fashionstore.identity.entity.UserAddress;
import com.fashionstore.identity.exception.IdentityErrorCode;
import com.fashionstore.identity.mapper.UserAddressMapperImpl;
import com.fashionstore.identity.mapper.UserMapperImpl;
import com.fashionstore.identity.repository.UserAddressRepository;
import com.fashionstore.identity.repository.UserRepository;
import com.fashionstore.identity.service.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminUserServiceImplTest {

    @Mock
    UserRepository userRepository;

    @Mock
    UserAddressRepository userAddressRepository;

    @Mock
    KeycloakAdminClient keycloakAdminClient;

    @Mock
    CurrentUserProvider currentUserProvider;

    AdminUserServiceImpl service;

    User customer;

    @BeforeEach
    void setUp() {
        service = new AdminUserServiceImpl(userRepository, userAddressRepository, keycloakAdminClient,
                currentUserProvider, new UserMapperImpl(), new UserAddressMapperImpl());
        customer = User.builder().email("customer@fashion.local").fullName("Nguyen Van A").phone("0901234567").build();
        customer.setId("kc-customer");
        when(currentUserProvider.getCurrentUserId()).thenReturn("kc-admin");
        when(userRepository.findById("kc-customer")).thenReturn(Optional.of(customer));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ----- khoá / mở khoá -----

    /** Khoá ở Keycloak trước (không lấy được token mới) rồi huỷ mọi phiên, cuối cùng mới ghi is_active. */
    @Test
    void lockingDisablesInKeycloakThenEndsSessionsThenMarksInactive() {
        AdminUserResponse response = service.updateStatus("kc-customer", false);

        assertThat(response.getIsActive()).isFalse();
        InOrder order = inOrder(keycloakAdminClient, userRepository);
        order.verify(keycloakAdminClient).setEnabled("kc-customer", false);
        order.verify(keycloakAdminClient).logout("kc-customer");
        order.verify(userRepository).save(customer);
        assertThat(customer.getIsActive()).isFalse();
    }

    @Test
    void unlockingReEnablesInKeycloakWithoutLoggingOut() {
        customer.setIsActive(false);

        AdminUserResponse response = service.updateStatus("kc-customer", true);

        assertThat(response.getIsActive()).isTrue();
        verify(keycloakAdminClient).setEnabled("kc-customer", true);
        verify(keycloakAdminClient, never()).logout(anyString());
        assertThat(customer.getIsActive()).isTrue();
    }

    /** Keycloak lỗi thì identity không được báo "đã khoá" trong khi người dùng vẫn đăng nhập được. */
    @Test
    void whenKeycloakFails_statusIsNotChanged() {
        doThrow(new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR))
                .when(keycloakAdminClient).setEnabled("kc-customer", false);

        assertThatThrownBy(() -> service.updateStatus("kc-customer", false))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UPSTREAM_SERVICE_ERROR);
        assertThat(customer.getIsActive()).isTrue();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void adminCannotLockThemselves() {
        User admin = User.builder().email("admin@fashion.local").fullName("Admin").build();
        admin.setId("kc-admin");
        when(userRepository.findById("kc-admin")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.updateStatus("kc-admin", false))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(IdentityErrorCode.CANNOT_CHANGE_OWN_STATUS);
        verify(keycloakAdminClient, never()).setEnabled(anyString(), anyBoolean());
    }

    @Test
    void lockingUnknownUser_throwsNotFoundWithoutTouchingKeycloak() {
        when(userRepository.findById("kc-unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus("kc-unknown", false))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(IdentityErrorCode.USER_NOT_FOUND);
        verify(keycloakAdminClient, never()).setEnabled(anyString(), anyBoolean());
    }

    // ----- cập nhật thông tin cơ bản -----

    @Test
    void updateUser_changesNameAndPhoneOnly() {
        AdminUserResponse response = service.updateUser("kc-customer",
                new AdminUpdateUserRequest("Tran Thi B", "0912345678"));

        assertThat(response.getFullName()).isEqualTo("Tran Thi B");
        assertThat(response.getPhone()).isEqualTo("0912345678");
        assertThat(customer.getEmail()).isEqualTo("customer@fashion.local");
        assertThat(customer.getIsActive()).isTrue();
        verify(userRepository).save(customer);
        verify(keycloakAdminClient, never()).setEnabled(anyString(), anyBoolean());
    }

    @Test
    void updateUser_unknownUser_throwsNotFound() {
        when(userRepository.findById("kc-unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateUser("kc-unknown", new AdminUpdateUserRequest("B", "0912345678")))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(IdentityErrorCode.USER_NOT_FOUND);
        verify(userRepository, never()).save(any(User.class));
    }

    // ----- tra cứu -----

    @Test
    void getUser_returnsProfileWithAddressBook() {
        UserAddress address = UserAddress.builder().userId("kc-customer").recipientName("A").phone("0901234567")
                .province("Ho Chi Minh").district("Quan 1").ward("Ben Nghe").detailAddress("123 Le Loi")
                .isDefault(true).build();
        address.setId("addr-1");
        when(userAddressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc("kc-customer"))
                .thenReturn(List.of(address));

        AdminUserDetailResponse detail = service.getUser("kc-customer");

        assertThat(detail.getId()).isEqualTo("kc-customer");
        assertThat(detail.getEmail()).isEqualTo("customer@fashion.local");
        assertThat(detail.getIsActive()).isTrue();
        assertThat(detail.getAddresses()).singleElement()
                .satisfies(a -> assertThat(a.getFullAddress()).isEqualTo("123 Le Loi, Ben Nghe, Quan 1, Ho Chi Minh"));
    }

    @Test
    void getUser_unknownUser_throwsNotFound() {
        when(userRepository.findById("kc-unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUser("kc-unknown"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(IdentityErrorCode.USER_NOT_FOUND);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchUsers_returnsPageOfSummaries() {
        Pageable pageable = PageRequest.of(0, 20);
        when(userRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(customer), pageable, 1));

        PageResponse<List<AdminUserResponse>> page = service.searchUsers("nguyen", true, pageable);

        assertThat(page.getItems()).singleElement()
                .satisfies(u -> assertThat(u.getEmail()).isEqualTo("customer@fashion.local"));
        assertThat(page.getTotalPage()).isEqualTo(1);
        assertThat(page.getPageSize()).isEqualTo(20);
    }
}
