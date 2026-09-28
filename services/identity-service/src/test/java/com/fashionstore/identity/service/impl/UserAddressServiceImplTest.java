package com.fashionstore.identity.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.identity.dto.user.UserAddressResponse;
import com.fashionstore.identity.entity.UserAddress;
import com.fashionstore.identity.exception.IdentityErrorCode;
import com.fashionstore.identity.mapper.UserAddressMapper;
import com.fashionstore.identity.repository.UserAddressRepository;
import com.fashionstore.identity.service.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAddressServiceImplTest {

    @Mock
    UserAddressRepository userAddressRepository;

    @Mock
    CurrentUserProvider currentUserProvider;

    @Mock
    UserAddressMapper userAddressMapper;

    @InjectMocks
    UserAddressServiceImpl service;

    UserAddress address;

    @BeforeEach
    void setUp() {
        address = UserAddress.builder().userId("user-a").recipientName("A").build();
        address.setId("addr-a");
        lenient().when(userAddressMapper.toResponse(address))
                .thenReturn(UserAddressResponse.builder().id("addr-a").build());
    }

    @Test
    void getAddressById_returnsOwnAddress() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-a");
        when(userAddressRepository.findByIdAndUserId("addr-a", "user-a")).thenReturn(Optional.of(address));

        assertThat(service.getAddressById("addr-a").getId()).isEqualTo("addr-a");
    }

    @Test
    void getAddressById_ofAnotherUser_throwsNotFound() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-b");
        when(userAddressRepository.findByIdAndUserId("addr-a", "user-b")).thenReturn(Optional.empty());
        lenient().when(userAddressRepository.findById("addr-a")).thenReturn(Optional.of(address));

        assertThatThrownBy(() -> service.getAddressById("addr-a"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(IdentityErrorCode.ADDRESS_NOT_FOUND);
    }

    @Test
    void getAddressOfUser_returnsAddressOwnedByGivenUser() {
        when(userAddressRepository.findByIdAndUserId("addr-a", "user-a")).thenReturn(Optional.of(address));

        assertThat(service.getAddressOfUser("user-a", "addr-a").getId()).isEqualTo("addr-a");
    }

    @Test
    void getAddressOfUser_whenAddressBelongsToAnotherUser_throwsNotFound() {
        when(userAddressRepository.findByIdAndUserId("addr-a", "user-b")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAddressOfUser("user-b", "addr-a"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(IdentityErrorCode.ADDRESS_NOT_FOUND);
    }
}
