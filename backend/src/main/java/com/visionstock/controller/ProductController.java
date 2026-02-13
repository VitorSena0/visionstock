package com.visionstock.controller;

import com.visionstock.dto.ProductCreateDTO;
import com.visionstock.dto.ProductResponseDTO;
import com.visionstock.dto.ProductUpdateDTO;
import com.visionstock.dto.ValidationRequestDTO;
import com.visionstock.model.enums.UserRole;
import com.visionstock.model.inventory.Product;
import com.visionstock.service.ProductService;
import com.visionstock.service.ProductService.ProductUpdateResponse;
import com.visionstock.service.ValidationService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    private final ProductService productService;
    private final ValidationService validationService;

    public ProductController(ProductService productService, ValidationService validationService) {
        this.productService = productService;
        this.validationService = validationService;
    }

    /**
     * Creates a new product.
     * POST /api/v1/products
     */
    @PostMapping
    public ResponseEntity<ProductResponseDTO> createProduct(
            @Valid @RequestBody ProductCreateDTO dto) {

        logger.info("POST /api/v1/products - Creating product with ID: {}", dto.getId());
        ProductResponseDTO response = productService.createProduct(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Updates a product following the approval workflow.
     * 
     * For ADMIN users:
     * - Changes are applied immediately
     * - Returns HTTP 200 with updated product
     *
     * For USER (Estoquista) users:
     * - A ValidationRequest is created (product NOT updated yet)
     * - Returns HTTP 202 (Accepted) with validation request ID
     * - Admin must approve the request for changes to take effect
     *
     * Query Parameters:
     * - role: The user's role (ADMIN or USER). Default: USER
     * - userId: UUID of the user making the request
     *   
     * Example: PUT /api/v1/products/{id}?role=USER&userId=<UUID>
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProductUpdateResponse> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody ProductUpdateDTO dto,
            @RequestParam(name = "role", defaultValue = "USER") String roleParam,
            @RequestParam(name = "userId", defaultValue = "00000000-0000-0000-0000-000000000001") String userIdParam) {

        logger.info("PUT /api/v1/products/{} - Updating product with role: {}", id, roleParam);

        // Parse role
        UserRole role = UserRole.valueOf(roleParam.toUpperCase());
        UUID userId = UUID.fromString(userIdParam);

        // Call service
        ProductUpdateResponse response = productService.updateProduct(id, dto, role, userId);

        // Return appropriate HTTP status based on whether change was applied immediately
        if ("UPDATED".equals(response.getStatus())) {
            logger.info("Product {} updated immediately (ADMIN)", id);
            return ResponseEntity.ok(response);
        } else {
            logger.info("Validation request created for product {}", id);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        }
    }

    /**
     * Lists all pending validation requests (the approval queue).
     * Only available to ADMIN users.
     *
     * Returns a list of ValidationRequestDTO objects showing:
     * - What product is being modified
     * - What changed (originalData vs newData)
     * - Who requested the change
     * - When it was requested
     *
     * GET /api/v1/validation
     */
    @GetMapping("/validation")
    public ResponseEntity<List<ValidationRequestDTO>> listPendingValidations() {
        logger.info("GET /api/v1/validation - Listing pending validation requests");

        List<ValidationRequestDTO> requests = validationService.getPendingRequests();
        return ResponseEntity.ok(requests);
    }

    /**
     * Approves a validation request and applies the changes to the product.
     * Only accessible to ADMIN users.
     *
     * This operation is ATOMIC:
     * - If the product update succeeds, the request is marked as APPROVED
     * - If the product update fails, the request remains PENDING
     *
     * Request body (optional):
     * {
     *   "reviewNote": "Optional explanation for the approval"
     * }
     *
     * POST /api/v1/validation/{id}/approve?adminId=<UUID>
     */
    @PostMapping("/validation/{id}/approve")
    public ResponseEntity<ProductResponseDTO> approveValidation(
            @PathVariable UUID id,
            @RequestParam(name = "adminId", defaultValue = "00000000-0000-0000-0000-000000000002") String adminIdParam,
            @RequestBody(required = false) ApprovalRequest request) {

        logger.info("POST /api/v1/validation/{}/approve - Approving by admin {}", id, adminIdParam);

        UUID adminId = UUID.fromString(adminIdParam);
        String reviewNote = (request != null) ? request.getReviewNote() : null;

        // Service applies changes and marks request as approved (atomic)
        Product product = validationService.approveRequest(id, adminId, reviewNote);

        logger.info("Validation request {} approved - changes applied to product {}", id, product.getId());

        return ResponseEntity.ok(ProductResponseDTO.fromEntity(product));
    }

    /**
     * Rejects a validation request without applying changes.
     * Only accessible to ADMIN users.
     *
     * The product remains UNCHANGED after rejection.
     *
     * Request body (optional):
     * {
     *   "reviewNote": "Reason for rejection (e.g., 'Preço já está competitivo')"
     * }
     *
     * POST /api/v1/validation/{id}/reject?adminId=<UUID>
     */
    @PostMapping("/validation/{id}/reject")
    public ResponseEntity<ProductResponseDTO> rejectValidation(
            @PathVariable UUID id,
            @RequestParam(name = "adminId", defaultValue = "00000000-0000-0000-0000-000000000002") String adminIdParam,
            @RequestBody(required = false) ApprovalRequest request) {

        logger.info("POST /api/v1/validation/{}/reject - Rejecting by admin {}", id, adminIdParam);

        UUID adminId = UUID.fromString(adminIdParam);
        String reviewNote = (request != null) ? request.getReviewNote() : null;

        // Service marks request as rejected (NO changes to product)
        Product product = validationService.rejectRequest(id, adminId, reviewNote);

        logger.info("Validation request {} rejected - product {} unchanged", id, product.getId());

        return ResponseEntity.ok(ProductResponseDTO.fromEntity(product));
    }

    /**
     * Gets the validation request history for a specific product.
     *
     * GET /api/v1/products/{id}/validations
     */
    @GetMapping("/{id}/validations")
    public ResponseEntity<List<ValidationRequestDTO>> getProductValidations(
            @PathVariable UUID id) {

        logger.info("GET /api/v1/products/{}/validations - Getting validation history", id);

        List<ValidationRequestDTO> requests = validationService.getRequestsByProduct(id);
        return ResponseEntity.ok(requests);
    }

    /**
     * Helper class for approval/rejection request body.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApprovalRequest {
        private String reviewNote;
    }
}
