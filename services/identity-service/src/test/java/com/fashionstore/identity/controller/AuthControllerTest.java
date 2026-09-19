package com.fashionstore.identity.controller;

import com.fashionstore.identity.client.OrderServiceClient;
import com.fashionstore.identity.dto.auth.AuthResponse;
import com.fashionstore.identity.dto.auth.AuthResult;
import com.fashionstore.identity.dto.auth.LoginRequest;
import com.fashionstore.identity.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    AuthService authService;

    @Mock
    OrderServiceClient orderServiceClient;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, orderServiceClient))
                .build();
    }

    @Test
    void login_withAnonymousCookie_triggersMergeAndClearsCookie() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "Password@123");
        AuthResponse authResponse = AuthResponse.builder()
                .userId("user-100")
                .accessToken("mock-access-token")
                .build();
        AuthResult authResult = new AuthResult(authResponse, "mock-refresh-token");

        when(authService.login(any(LoginRequest.class), anyString())).thenReturn(authResult);
        when(orderServiceClient.triggerCartMerge(anyString(), anyString())).thenReturn(true);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .cookie(new Cookie("anonymous_id", "123e4567-e89b-12d3-a456-426614174000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("user-100"))
                .andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("anonymous_id="),
                                org.hamcrest.Matchers.containsString("Max-Age=0")
                        )
                )));

        verify(orderServiceClient).triggerCartMerge("user-100", "123e4567-e89b-12d3-a456-426614174000");
    }

    @Test
    void login_withAnonymousCookie_whenMergeFails_doesNotClearCookie() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "Password@123");
        AuthResponse authResponse = AuthResponse.builder()
                .userId("user-100")
                .accessToken("mock-access-token")
                .build();
        AuthResult authResult = new AuthResult(authResponse, "mock-refresh-token");

        when(authService.login(any(LoginRequest.class), anyString())).thenReturn(authResult);
        when(orderServiceClient.triggerCartMerge("user-100", "123e4567-e89b-12d3-a456-426614174000")).thenReturn(false);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .cookie(new Cookie("anonymous_id", "123e4567-e89b-12d3-a456-426614174000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("user-100"))
                .andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.allOf(
                                        org.hamcrest.Matchers.containsString("anonymous_id="),
                                        org.hamcrest.Matchers.containsString("Max-Age=0")
                                )
                        )
                )));

        verify(orderServiceClient).triggerCartMerge("user-100", "123e4567-e89b-12d3-a456-426614174000");
    }

    @Test
    void login_withoutAnonymousId_doesNotTriggerMerge() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "Password@123");
        AuthResponse authResponse = AuthResponse.builder()
                .userId("user-200")
                .accessToken("mock-access-token")
                .build();
        AuthResult authResult = new AuthResult(authResponse, "mock-refresh-token");

        when(authService.login(any(LoginRequest.class), anyString())).thenReturn(authResult);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("user-200"));

        verify(orderServiceClient, never()).triggerCartMerge(anyString(), anyString());
    }
}
