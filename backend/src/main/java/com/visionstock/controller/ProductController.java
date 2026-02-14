package com.visionstock.controller;

import com.visionstock.dto.ProductCreateDTO;
import com.visionstock.dto.ProductResponseDTO;
import com.visionstock.dto.ProductUpdateDTO;
import com.visionstock.dto.ValidationRequestDTO;
import com.visionstock.model.enums.UserRole;
import com.visionstock.security.AuthenticatedUser;
import com.visionstock.service.ProductService;
import com.visionstock.service.ProductService.ProductUpdateResponse;
import com.visionstock.service.ValidationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
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
            @Valid @RequestBody ProductCreateDTO dto,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        UUID userId = requireUserId(authenticatedUser);
        logger.info("POST /api/v1/products - Creating product with ID: {} by user {}", dto.getId(), userId);
        ProductResponseDTO response = productService.createProduct(dto, userId);
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
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProductUpdateResponse> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody ProductUpdateDTO dto,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        UUID userId = requireUserId(authenticatedUser);
        logger.info("PUT /api/v1/products/{} - Updating product by user {}", id, userId);

        UserRole role = UserRole.valueOf(authenticatedUser.getRole().toUpperCase(Locale.ROOT));

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

    private UUID requireUserId(AuthenticatedUser authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.getId() == null) {
            throw new AuthenticationCredentialsNotFoundException("Authenticated user not found");
        }
        return authenticatedUser.getId();
    }
}
