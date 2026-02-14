package com.visionstock.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionstock.controller.AuthController;
import com.visionstock.controller.ProductController;
import com.visionstock.controller.ScanController;
import com.visionstock.controller.ValidationController;
import com.visionstock.dto.AuthResponseDTO;
import com.visionstock.dto.LoginDTO;
import com.visionstock.dto.ProductResponseDTO;
import com.visionstock.service.AuthService;
import com.visionstock.service.GeminiService;
import com.visionstock.service.ProductService;
import com.visionstock.service.ValidationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        AuthController.class,
        ProductController.class,
        ValidationController.class,
        ScanController.class
})
@Import({
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class SecurityAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private ProductService productService;

    @MockBean
    private ValidationService validationService;

    @MockBean
    private GeminiService geminiService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private JwtService jwtService;

    @Test
    @DisplayName("/api/v1/auth/login should be public")
    void authEndpoint_shouldBePublic() throws Exception {
        AuthResponseDTO response = AuthResponseDTO.builder()
                .token("token")
                .role("USER")
                .userId(UUID.randomUUID())
                .build();
        when(authService.login(any(LoginDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                LoginDTO.builder()
                                        .email("user@visionstock.com")
                                        .password("SenhaForte123!")
                                        .build())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/products should return 401 without authentication")
    void products_withoutAuthentication_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id":"550e8400-e29b-41d4-a716-446655440000",
                                  "descricao":"Produto Teste",
                                  "precoVenda":89.90,
                                  "quantidadeInicial":10
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("GET /api/v1/validation should return 403 for USER role")
    void validation_withUserRole_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/validation")
                        .with(authentication(authenticatedUserToken("USER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("GET /api/v1/validation should allow ADMIN role")
    void validation_withAdminRole_shouldAllow() throws Exception {
        when(validationService.getPendingRequests()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/validation")
                        .with(authentication(authenticatedUserToken("ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/scan should allow USER role")
    void scan_withUserRole_shouldAllow() throws Exception {
        ProductResponseDTO response = ProductResponseDTO.builder()
                .descricao("Camiseta")
                .cor("Azul")
                .tamanho("M")
                .precoVenda(new BigDecimal("89.90"))
                .statusIa("IA_SUGERIDO")
                .statusValidacao("PENDENTE")
                .build();

        when(geminiService.extractDataFromImage(any(MultipartFile.class))).thenReturn(response);

        mockMvc.perform(multipart("/api/v1/scan")
                        .file("image", "fake-image-data".getBytes())
                        .with(authentication(authenticatedUserToken("USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao").value("Camiseta"));
    }

    private UsernamePasswordAuthenticationToken authenticatedUserToken(String role) {
        AuthenticatedUser principal = new AuthenticatedUser(
                UUID.randomUUID(),
                "user@visionstock.com",
                "$2a$10$dummyhashdummyhashdummyhashdum",
                role,
                true
        );

        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }
}
