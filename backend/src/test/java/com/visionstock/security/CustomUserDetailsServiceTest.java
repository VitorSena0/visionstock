package com.visionstock.security;

import com.visionstock.model.auth.User;
import com.visionstock.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    @DisplayName("loadUserByUsername should return authenticated user when active")
    void loadUserByUsername_activeUser_shouldReturnUserDetails() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@visionstock.com")
                .senhaHash("$2a$10$dummyhashdummyhashdummyhashdum")
                .role("USER")
                .ativo(true)
                .build();

        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("user@visionstock.com"))
                .thenReturn(Optional.of(user));

        CustomUserDetailsService service = new CustomUserDetailsService(userRepository);
        AuthenticatedUser details = (AuthenticatedUser) service.loadUserByUsername("user@visionstock.com");

        assertEquals(user.getId(), details.getId());
        assertEquals("user@visionstock.com", details.getUsername());
        assertEquals("USER", details.getRole());
        assertTrue(details.isEnabled());
    }

    @Test
    @DisplayName("loadUserByUsername should throw DisabledException when user is inactive")
    void loadUserByUsername_inactiveUser_shouldThrow() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("inactive@visionstock.com")
                .senhaHash("hash")
                .role("USER")
                .ativo(false)
                .build();

        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("inactive@visionstock.com"))
                .thenReturn(Optional.of(user));

        CustomUserDetailsService service = new CustomUserDetailsService(userRepository);

        assertThrows(DisabledException.class,
                () -> service.loadUserByUsername("inactive@visionstock.com"));
    }

    @Test
    @DisplayName("loadUserByUsername should throw UsernameNotFoundException when user is not found")
    void loadUserByUsername_notFound_shouldThrow() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("missing@visionstock.com"))
                .thenReturn(Optional.empty());

        CustomUserDetailsService service = new CustomUserDetailsService(userRepository);

        assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("missing@visionstock.com"));
    }
}
