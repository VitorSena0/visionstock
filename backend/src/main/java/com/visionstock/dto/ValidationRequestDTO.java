package com.visionstock.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.visionstock.model.enums.ValidationStatus;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for viewing a ValidationRequest in the admin panel.
 * Allows admins to see what changes are pending approval.
 * 
 * The originalData and newData are JSON snapshots that can be compared
 * to show the admin exactly what changed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationRequestDTO {

    private UUID id;

    private UUID productId;

    private String productReferencia;

    private String productDescricao;

    /**
     * UUID of the user (Estoquista) who requested this change.
     */
    private UUID requestedBy;

    private String requestedByName;

    /**
     * Current status: PENDING, APPROVED, or REJECTED
     */
    private ValidationStatus status;

    /**
     * JSON snapshot of product data BEFORE the requested changes.
     * Example: { "descricao": "Old Description", "cor": "Azul", "precoVenda": 50.00 }
     */
    private JsonNode originalData;

    /**
     * JSON snapshot of product data WITH the requested changes.
     * Example: { "descricao": "New Description", "cor": "Azul Marinho", "precoVenda": 55.00 }
     */
    private JsonNode newData;

    /**
     * When the change was requested.
     */
    private Instant requestedAt;

    /**
     * UUID of the admin who reviewed this request (if reviewed).
     */
    private UUID reviewedBy;

    private String reviewedByName;

    /**
     * Optional note explaining the approval/rejection decision.
     */
    private String reviewNote;

    /**
     * When the admin reviewed this request (if reviewed).
     */
    private Instant reviewedAt;

    /**
     * Summary of what changed (for display in the admin panel).
     * Example: "Descrição alterada, Cor alterada, Preço aumentado"
     */
    private String changesSummary;
}
