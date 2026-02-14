package com.visionstock.service;

import com.visionstock.dto.AuthResponseDTO;
import com.visionstock.dto.LoginDTO;
import com.visionstock.dto.RegisterDTO;
import com.visionstock.model.auth.User;
import com.visionstock.model.enums.UserRole;
import com.visionstock.repository.UserRepository;
import com.visionstock.security.AuthenticatedUser;
import com.visionstock.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponseDTO register(RegisterDTO dto) {
        String normalizedEmail = normalizeEmail(dto.getEmail());

        if (userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(normalizedEmail)) {
            throw new IllegalArgumentException("Email already registered");
        }

        String role = resolveRole(dto.getRole());

        User user = User.builder()
                .nome(dto.getNome().trim())
                .email(normalizedEmail)
                .senhaHash(passwordEncoder.encode(dto.getPassword()))
                .role(role)
                .ativo(true)
                .build();

        User savedUser = userRepository.save(user);
        AuthenticatedUser principal = AuthenticatedUser.fromEntity(savedUser);

        String token = jwtService.generateToken(principal);
        return AuthResponseDTO.builder()
                .token(token)
                .role(principal.getRole())
                .userId(principal.getId())
                .build();
    }

    @Transactional
    public AuthResponseDTO login(LoginDTO dto) {
        String normalizedEmail = normalizeEmail(dto.getEmail());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, dto.getPassword())
        );

        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();

        userRepository.findById(principal.getId()).ifPresent(user -> {
            user.setUltimoAcesso(Instant.now());
            userRepository.save(user);
        });

        String token = jwtService.generateToken(principal);
        return AuthResponseDTO.builder()
                .token(token)
                .role(principal.getRole())
                .userId(principal.getId())
                .build();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveRole(String role) {
        if (role == null || role.isBlank()) {
            return UserRole.USER.name();
        }

        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if (!UserRole.USER.name().equals(normalized)) {
            throw new IllegalArgumentException("Public registration only allows USER role");
        }
        return normalized;
    }
}
