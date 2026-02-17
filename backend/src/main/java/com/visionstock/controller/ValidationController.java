package com.visionstock.controller;

import com.visionstock.dto.ProductResponseDTO;
import com.visionstock.dto.ValidationDecisionDTO;
import com.visionstock.dto.ValidationRequestDTO;
import com.visionstock.model.inventory.Product;
import com.visionstock.security.AuthenticatedUser;
import com.visionstock.service.ValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/validation", "/api/v1/products/validation"})
public class ValidationController {

    private static final Logger logger = LoggerFactory.getLogger(ValidationController.class);

    private final ValidationService validationService;

    public ValidationController(ValidationService validationService) {
        this.validationService = validationService;
    }

    @GetMapping
    public ResponseEntity<List<ValidationRequestDTO>> listPendingValidations() {
        logger.info("GET /api/v1/validation - Listing pending validation requests");
        List<ValidationRequestDTO> requests = validationService.getPendingRequests();
        return ResponseEntity.ok(requests);
    }

    @GetMapping("/my")
    public ResponseEntity<List<ValidationRequestDTO>> listMyValidations(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        UUID userId = requireUserId(authenticatedUser);
        List<ValidationRequestDTO> requests = validationService.getRequestsByRequester(userId);
        return ResponseEntity.ok(requests);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ProductResponseDTO> approveValidation(
            @PathVariable UUID id,
            @RequestBody(required = false) ValidationDecisionDTO request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        UUID adminId = requireUserId(authenticatedUser);
        String reviewNote = request != null ? request.getReviewNote() : null;
        Product product = validationService.approveRequest(id, adminId, reviewNote);
        logger.info("Validation request {} approved by admin {}", id, adminId);

        return ResponseEntity.ok(ProductResponseDTO.fromEntity(product));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ProductResponseDTO> rejectValidation(
            @PathVariable UUID id,
            @RequestBody(required = false) ValidationDecisionDTO request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        UUID adminId = requireUserId(authenticatedUser);
        String reviewNote = request != null ? request.getReviewNote() : null;
        Product product = validationService.rejectRequest(id, adminId, reviewNote);
        logger.info("Validation request {} rejected by admin {}", id, adminId);

        return ResponseEntity.ok(ProductResponseDTO.fromEntity(product));
    }

    private UUID requireUserId(AuthenticatedUser authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.getId() == null) {
            throw new AuthenticationCredentialsNotFoundException("Authenticated user not found");
        }
        return authenticatedUser.getId();
    }
}
