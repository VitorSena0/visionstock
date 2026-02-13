package com.visionstock.model.enums;

/**
 * Enum for validation request statuses.
 * Represents the lifecycle of a product update request:
 * - PENDING: Aguardando aprovação do gerente
 * - APPROVED: Aprovado e aplicado ao produto
 * - REJECTED: Rejeitado pelo gerente
 */
public enum ValidationStatus {
    PENDING("Aguardando aprovação"),
    APPROVED("Aprovado"),
    REJECTED("Rejeitado");

    private final String description;

    ValidationStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if this status is pending approval.
     */
    public boolean isPending() {
        return this == PENDING;
    }

    /**
     * Check if this status is approved.
     */
    public boolean isApproved() {
        return this == APPROVED;
    }

    /**
     * Check if this status is rejected.
     */
    public boolean isRejected() {
        return this == REJECTED;
    }
}
