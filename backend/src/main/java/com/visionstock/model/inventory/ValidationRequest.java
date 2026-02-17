package com.visionstock.model.inventory;

import com.visionstock.model.enums.ValidationStatus;
import com.visionstock.model.enums.ValidationChangeType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a validation request for product updates.
 * This is the "Guard-Rail" mechanism that prevents unauthorized product changes.
 * 
 * Workflow:
 * 1. Estoquista (USER) submits a product update -> creates a PENDING ValidationRequest
 * 2. Gerente (ADMIN) reviews the request
 * 3. If approved -> applies changes to Product and sets status to APPROVED
 * 4. If rejected -> sets status to REJECTED (Product remains unchanged)
 */
@Entity
@Table(name = "validation_queue", schema = "inventory")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationRequest {

    @Id
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_id", insertable = false, updatable = false)
    private UUID productId;

    /**
     * User ID of the person who requested the validation.
     * This is the estoquista who submitted the change.
     */
    @Column(name = "user_id", nullable = false)
    private UUID requestedBy;

    /**
     * Status of the validation request.
     * Possible values: PENDING, APPROVED, REJECTED
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ValidationStatus status = ValidationStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 30)
    @Builder.Default
    private ValidationChangeType changeType = ValidationChangeType.PRODUCT_FIELDS;

    /**
     * JSON snapshot of the original product data before the requested changes.
     * This allows the admin to see what changed at a glance.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dados_anteriores", columnDefinition = "jsonb", nullable = false)
    private String originalData;

    /**
     * JSON snapshot of the proposed new product data.
     * If approved, these values will be applied to the Product entity.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dados_novos", columnDefinition = "jsonb", nullable = false)
    private String newData;

    /**
     * Timestamp when the validation request was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant requestedAt;

    /**
     * Optional note or comment from the admin reviewing the request.
     */
    @Column(name = "observacao")
    private String reviewNote;

    /**
     * User ID of the admin who approved or rejected this request.
     */
    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    /**
     * Timestamp when the admin reviewed (approved or rejected) the request.
     */
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /**
     * Timestamp when the record was last updated.
     */
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        requestedAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Marks this request as approved by an admin.
     * Sets the review timestamp and reviewer.
     */
    public void approve(UUID adminId) {
        this.status = ValidationStatus.APPROVED;
        this.reviewedBy = adminId;
        this.reviewedAt = Instant.now();
    }

    /**
     * Marks this request as rejected by an admin.
     * Sets the review timestamp and reviewer.
     */
    public void reject(UUID adminId) {
        this.status = ValidationStatus.REJECTED;
        this.reviewedBy = adminId;
        this.reviewedAt = Instant.now();
    }

    /**
     * Checks if this request is still pending review.
     */
    public boolean isPending() {
        return status == ValidationStatus.PENDING;
    }

    /**
     * Checks if this request has been approved.
     */
    public boolean isApproved() {
        return status == ValidationStatus.APPROVED;
    }

    /**
     * Checks if this request has been rejected.
     */
    public boolean isRejected() {
        return status == ValidationStatus.REJECTED;
    }
}
