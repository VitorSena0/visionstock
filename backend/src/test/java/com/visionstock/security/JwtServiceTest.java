package com.visionstock.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.jsonwebtoken.ExpiredJwtException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-with-at-least-32-characters-visionstock";

    @Test
    @DisplayName("generateToken should include username, role and userId claims")
    void generateToken_shouldIncludeClaims() {
        JwtService jwtService = new JwtService(SECRET, 60_000);
        AuthenticatedUser user = new AuthenticatedUser(
                UUID.randomUUID(),
                "admin@visionstock.com",
                "$2a$10$dummyhashdummyhashdummyhashdum",
                "ADMIN",
                true
        );

        String token = jwtService.generateToken(user);

        assertNotNull(token);
        assertEquals("admin@visionstock.com", jwtService.extractUsername(token));
        assertEquals("ADMIN", jwtService.extractRole(token));
        assertEquals(user.getId(), jwtService.extractUserId(token));
        assertTrue(jwtService.isTokenValid(token, user));
    }

    @Test
    @DisplayName("isTokenValid should return false for a different username")
    void isTokenValid_differentUser_shouldReturnFalse() {
        JwtService jwtService = new JwtService(SECRET, 60_000);
        AuthenticatedUser tokenOwner = new AuthenticatedUser(
                UUID.randomUUID(),
                "user1@visionstock.com",
                "hash",
                "USER",
                true
        );
        AuthenticatedUser anotherUser = new AuthenticatedUser(
                UUID.randomUUID(),
                "user2@visionstock.com",
                "hash",
                "USER",
                true
        );

        String token = jwtService.generateToken(tokenOwner);
        assertFalse(jwtService.isTokenValid(token, anotherUser));
    }

    @Test
    @DisplayName("isTokenValid should throw when token is already expired")
    void isTokenValid_expiredToken_shouldThrow() {
        JwtService jwtService = new JwtService(SECRET, -1);
        AuthenticatedUser user = new AuthenticatedUser(
                UUID.randomUUID(),
                "user@visionstock.com",
                "hash",
                "USER",
                true
        );

        String token = jwtService.generateToken(user);
        assertThrows(ExpiredJwtException.class, () -> jwtService.isTokenValid(token, user));
    }

    @Test
    @DisplayName("constructor should throw when secret key is too short")
    void constructor_shortSecret_shouldThrow() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new JwtService("short-secret", 60_000));

        assertTrue(ex.getMessage().contains("at least 32 characters"));
    }
}
