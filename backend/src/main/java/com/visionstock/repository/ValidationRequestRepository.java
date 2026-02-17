package com.visionstock.repository;

import com.visionstock.model.inventory.ValidationRequest;
import com.visionstock.model.enums.ValidationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ValidationRequestRepository extends JpaRepository<ValidationRequest, UUID> {

    /**
     * Find all pending validation requests (items in the approval queue).
     */
    List<ValidationRequest> findByStatusOrderByRequestedAtAsc(ValidationStatus status);

    /**
     * Find all validation requests for a specific product.
     */
    List<ValidationRequest> findByProductIdOrderByRequestedAtDesc(UUID productId);

    List<ValidationRequest> findByRequestedByOrderByRequestedAtDesc(UUID requestedBy);

    /**
     * Find all pending validation requests for a specific product.
     */
    List<ValidationRequest> findByProductIdAndStatusOrderByRequestedAtDesc(
            UUID productId, ValidationStatus status);

    /**
     * Find a validation request by ID and ensure it exists.
     */
    Optional<ValidationRequest> findById(UUID id);

    /**
     * Count pending validation requests.
     */
    Long countByStatus(ValidationStatus status);
}
