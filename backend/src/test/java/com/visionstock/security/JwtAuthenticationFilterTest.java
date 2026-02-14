package com.visionstock.security;

import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private CustomUserDetailsService customUserDetailsService;

    @Mock
    private FilterChain filterChain;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("doFilter should set authentication when token is valid")
    void doFilter_validToken_shouldAuthenticate() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, customUserDetailsService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AuthenticatedUser user = new AuthenticatedUser(
                UUID.randomUUID(),
                "user@visionstock.com",
                "hash",
                "USER",
                true
        );

        request.addHeader("Authorization", "Bearer valid-token");
        when(jwtService.extractUsername("valid-token")).thenReturn("user@visionstock.com");
        when(customUserDetailsService.loadUserByUsername("user@visionstock.com")).thenReturn(user);
        when(jwtService.isTokenValid("valid-token", user)).thenReturn(true);

        filter.doFilter(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("doFilter should ignore request when authorization header is missing")
    void doFilter_noAuthorizationHeader_shouldSkipAuthentication() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, customUserDetailsService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtService, customUserDetailsService);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("doFilter should clear context when token parsing fails")
    void doFilter_invalidToken_shouldClearContext() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, customUserDetailsService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.addHeader("Authorization", "Bearer invalid-token");
        when(jwtService.extractUsername(anyString())).thenThrow(new MalformedJwtException("invalid"));

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }
}
