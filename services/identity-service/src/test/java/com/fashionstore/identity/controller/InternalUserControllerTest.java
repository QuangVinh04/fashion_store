package com.fashionstore.identity.controller;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.GlobalExceptionHandler;
import com.fashionstore.identity.dto.user.UserAddressResponse;
import com.fashionstore.identity.entity.User;
import com.fashionstore.identity.exception.IdentityErrorCode;
import com.fashionstore.identity.repository.UserRepository;
import com.fashionstore.identity.service.UserAddressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalUserControllerTest {

    MockMvc mockMvc;

    @Mock
    UserRepository userRepository;

    @Mock
    UserAddressService userAddressService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InternalUserController(userRepository, userAddressService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getUserById_whenUserExists_returns200AndData() throws Exception {
        User user = User.builder()
                .email("john@example.com")
                .fullName("John Doe")
                .phone("0912345678")
                .build();
        user.setId("user-123");

        when(userRepository.findById("user-123")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/internal/v1/users/user-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("user-123"))
                .andExpect(jsonPath("$.data.email").value("john@example.com"))
                .andExpect(jsonPath("$.data.fullName").value("John Doe"))
                .andExpect(jsonPath("$.data.phone").value("0912345678"));
    }

    @Test
    void getUserById_whenUserNotFound_returns404() throws Exception {
        when(userRepository.findById("user-not-found")).thenReturn(Optional.empty());

        mockMvc.perform(get("/internal/v1/users/user-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void getAddressOfUser_returnsAddressOwnedByThatUser() throws Exception {
        when(userAddressService.getAddressOfUser("user-123", "addr-1"))
                .thenReturn(UserAddressResponse.builder().id("addr-1").districtId(1444).wardCode("20308").build());

        mockMvc.perform(get("/internal/v1/users/user-123/addresses/addr-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("addr-1"))
                .andExpect(jsonPath("$.data.districtId").value(1444))
                .andExpect(jsonPath("$.data.wardCode").value("20308"));
    }

    @Test
    void getAddressOfUser_whenNotOwnedByThatUser_returns404() throws Exception {
        when(userAddressService.getAddressOfUser("user-123", "addr-other"))
                .thenThrow(new AppException(IdentityErrorCode.ADDRESS_NOT_FOUND));

        mockMvc.perform(get("/internal/v1/users/user-123/addresses/addr-other"))
                .andExpect(status().isNotFound());
    }
}
