package com.visionstock.service;

import com.visionstock.dto.AuthResponseDTO;
import com.visionstock.dto.LoginDTO;
import com.visionstock.dto.RegisterDTO;
import com.visionstock.model.auth.User;
import com.visionstock.repository.UserRepository;
import com.visionstock.security.AuthenticatedUser;
import com.visionstock.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("register should create user with normalized email and return JWT response")
    void register_shouldCreateUserAndReturnToken() {
        UUID savedUserId = UUID.randomUUID();
        RegisterDTO dto = RegisterDTO.builder()
                .nome("  Maria Oliveira ")
                .email("  Maria@VisionStock.COM ")
                .password("SenhaForte123!")
                .build();

        when(userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull("maria@visionstock.com"))
                .thenReturn(false);
        when(passwordEncoder.encode("SenhaForte123!")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(savedUserId);
            return user;
        });
        when(jwtService.generateToken(any(AuthenticatedUser.class))).thenReturn("jwt-token");

        AuthResponseDTO response = authService.register(dto);

        assertNotNull(response);
        assertEquals("jwt-token", response.getToken());
        assertEquals("USER", response.getRole());
        assertEquals(savedUserId, response.getUserId());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();
        assertEquals("Maria Oliveira", savedUser.getNome());
        assertEquals("maria@visionstock.com", savedUser.getEmail());
        assertEquals("hashed-password", savedUser.getSenhaHash());
        assertEquals("USER", savedUser.getRole());
        assertTrue(Boolean.TRUE.equals(savedUser.getAtivo()));
    }

    @Test
    @DisplayName("register should throw when email is already registered")
    void register_duplicateEmail_shouldThrow() {
        RegisterDTO dto = RegisterDTO.builder()
                .nome("Maria")
                .email("maria@visionstock.com")
                .password("SenhaForte123!")
                .build();

        when(userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull("maria@visionstock.com"))
                .thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.register(dto));

        assertEquals("Email already registered", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register should reject role different from USER")
    void register_invalidRole_shouldThrow() {
        RegisterDTO dto = RegisterDTO.builder()
                .nome("Admin")
                .email("admin@visionstock.com")
                .password("SenhaForte123!")
                .role("ADMIN")
                .build();

        when(userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull("admin@visionstock.com"))
                .thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.register(dto));

        assertEquals("Public registration only allows USER role", ex.getMessage());
    }

    @Test
    @DisplayName("login should authenticate with normalized email, update last access and return JWT response")
    void login_shouldAuthenticateAndReturnToken() {
        UUID userId = UUID.randomUUID();
        LoginDTO dto = LoginDTO.builder()
                .email("  USER@VisionStock.com ")
                .password("SenhaForte123!")
                .build();

        AuthenticatedUser principal = new AuthenticatedUser(
                userId,
                "user@visionstock.com",
                "hash",
                "USER",
                true
        );
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());

        User dbUser = User.builder()
                .id(userId)
                .email("user@visionstock.com")
                .nome("User")
                .senhaHash("hash")
                .role("USER")
                .ativo(true)
                .build();

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findById(userId)).thenReturn(Optional.of(dbUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken(principal)).thenReturn("jwt-login-token");

        AuthResponseDTO response = authService.login(dto);

        assertEquals("jwt-login-token", response.getToken());
        assertEquals("USER", response.getRole());
        assertEquals(userId, response.getUserId());

        ArgumentCaptor<UsernamePasswordAuthenticationToken> authCaptor =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(authCaptor.capture());
        assertEquals("user@visionstock.com", authCaptor.getValue().getPrincipal());
        assertEquals("SenhaForte123!", authCaptor.getValue().getCredentials());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertNotNull(userCaptor.getValue().getUltimoAcesso());
    }
}
