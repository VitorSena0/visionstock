package com.visionstock.security;

import com.visionstock.model.auth.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class AuthenticatedUser implements UserDetails {

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final String role;
    private final boolean enabled;

    public AuthenticatedUser(UUID id, String email, String passwordHash, String role, boolean enabled) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = enabled;
    }

    public static AuthenticatedUser fromEntity(User user) {
        String normalizedRole = user.getRole() == null ? "USER" : user.getRole().toUpperCase(Locale.ROOT);
        boolean active = Boolean.TRUE.equals(user.getAtivo());
        return new AuthenticatedUser(
                user.getId(),
                user.getEmail(),
                user.getSenhaHash(),
                normalizedRole,
                active
        );
    }

    public UUID getId() {
        return id;
    }

    public String getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
