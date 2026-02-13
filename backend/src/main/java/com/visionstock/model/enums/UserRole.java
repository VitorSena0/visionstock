package com.visionstock.model.enums;

/**
 * Enum for user roles in the VisionStock system.
 * Defines the two main roles:
 * - ADMIN: Gerente (can approve/reject product changes)
 * - USER: Estoquista (can request product changes)
 */
public enum UserRole {
    ADMIN("Administrator - Gerente"),
    USER("User - Estoquista");

    private final String description;

    UserRole(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if this role is an administrator.
     */
    public boolean isAdmin() {
        return this == ADMIN;
    }

    /**
     * Check if this role is a regular user.
     */
    public boolean isUser() {
        return this == USER;
    }
}
