package com.fashionstore.identity.controller;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.GlobalExceptionHandler;
import com.fashionstore.identity.dto.user.AdminUpdateUserRequest;
import com.fashionstore.identity.dto.user.AdminUserResponse;
import com.fashionstore.identity.service.AdminUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    MockMvc mockMvc;

    @Mock
    AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminUserController(adminUserService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void searchPassesKeywordAndStatusFilter() throws Exception {
        when(adminUserService.searchUsers(eq("nguyen"), eq(false), any(Pageable.class)))
                .thenReturn(PageResponse.<List<AdminUserResponse>>builder()
                        .items(List.of(AdminUserResponse.builder().id("kc-1").isActive(false).build()))
                        .build());

        mockMvc.perform(get("/api/v1/admin/users").param("keyword", "nguyen").param("isActive", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value("kc-1"))
                .andExpect(jsonPath("$.data.items[0].isActive").value(false));
    }

    @Test
    void lockUser() throws Exception {
        when(adminUserService.updateStatus("kc-1", false))
                .thenReturn(AdminUserResponse.builder().id("kc-1").isActive(false).build());

        mockMvc.perform(patch("/api/v1/admin/users/kc-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(false));
    }

    @Test
    void statusRequestWithoutActiveFlagIsRejected() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/kc-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(adminUserService, never()).updateStatus(any(), any(Boolean.class));
    }

    @Test
    void updateUserInfo() throws Exception {
        when(adminUserService.updateUser(eq("kc-1"), any(AdminUpdateUserRequest.class)))
                .thenReturn(AdminUserResponse.builder().id("kc-1").fullName("Tran Thi B").phone("0912345678").build());

        mockMvc.perform(put("/api/v1/admin/users/kc-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \"Tran Thi B\", \"phone\": \"0912345678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Tran Thi B"));
    }

    @Test
    void updateUserInfo_rejectsBlankNameAndInvalidPhone() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/kc-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \" \", \"phone\": \"0912345678\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/admin/users/kc-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \"Tran Thi B\", \"phone\": \"12ab\"}"))
                .andExpect(status().isBadRequest());

        verify(adminUserService, never()).updateUser(any(), any());
    }
}
