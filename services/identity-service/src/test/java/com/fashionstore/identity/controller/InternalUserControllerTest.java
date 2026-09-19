package com.fashionstore.identity.controller;

import com.fashionstore.common.exception.GlobalExceptionHandler;
import com.fashionstore.identity.entity.User;
import com.fashionstore.identity.repository.UserRepository;
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

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InternalUserController(userRepository))
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
}
