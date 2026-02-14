package com.visionstock.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionstock.dto.AuthResponseDTO;
import com.visionstock.dto.LoginDTO;
import com.visionstock.dto.RegisterDTO;
import com.visionstock.security.JwtAuthenticationFilter;
import com.visionstock.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    @DisplayName("POST /api/v1/auth/register should return 201 with token payload")
    void register_shouldReturn201() throws Exception {
        UUID userId = UUID.randomUUID();
        RegisterDTO request = RegisterDTO.builder()
                .nome("Maria Oliveira")
                .email("maria@visionstock.com")
                .password("SenhaForte123!")
                .build();

        AuthResponseDTO response = AuthResponseDTO.builder()
                .token("jwt-token")
                .role("USER")
                .userId(userId)
                .build();

        when(authService.register(any(RegisterDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login should return 200 with token payload")
    void login_shouldReturn200() throws Exception {
        UUID userId = UUID.randomUUID();
        LoginDTO request = LoginDTO.builder()
                .email("maria@visionstock.com")
                .password("SenhaForte123!")
                .build();

        AuthResponseDTO response = AuthResponseDTO.builder()
                .token("jwt-login-token")
                .role("USER")
                .userId(userId)
                .build();

        when(authService.login(any(LoginDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-login-token"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register should return 400 when required fields are missing")
    void register_invalidPayload_shouldReturn400() throws Exception {
        RegisterDTO request = RegisterDTO.builder()
                .nome("Maria")
                .password("123")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
