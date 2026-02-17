package com.visionstock.controller;

import com.visionstock.dto.ActionResponseDTO;
import com.visionstock.dto.ProductCreateDTO;
import com.visionstock.dto.ProductAdminDTO;
import com.visionstock.dto.ProductImageMetadataDTO;
import com.visionstock.dto.ProductResponseDTO;
import com.visionstock.dto.StockAdjustmentDTO;
import com.visionstock.dto.ProductUpdateDTO;
import com.visionstock.dto.ValidationRequestDTO;
import com.visionstock.model.enums.UserRole;
import com.visionstock.model.inventory.ProductImage;
import com.visionstock.security.AuthenticatedUser;
import com.visionstock.service.ProductImageService;
import com.visionstock.service.ProductService;
import com.visionstock.service.ProductService.ProductUpdateResponse;
import com.visionstock.service.ValidationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    private final ProductService productService;
    private final ProductImageService productImageService;
    private final ValidationService validationService;

    public ProductController(ProductService productService,
                             ProductImageService productImageService,
                             ValidationService validationService) {
        this.productService = productService;
        this.productImageService = productImageService;
        this.validationService = validationService;
    }

    /**
     * Lists products for sync/read operations.
     *
     * GET /api/v1/products
     *
     * USER: returns ProductResponseDTO list (without financial fields)
     * ADMIN: returns ProductAdminDTO list (includes financial fields)
     */
    @GetMapping
    public ResponseEntity<List<?>> listProducts(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        UUID userId = requireUserId(authenticatedUser);
        UserRole role = UserRole.valueOf(authenticatedUser.getRole().toUpperCase(Locale.ROOT));

        logger.info("GET /api/v1/products - Listing products for user {} with role {}", userId, role);

        if (role.isAdmin()) {
            List<ProductAdminDTO> products = productService.listProductsForAdmin();
            return ResponseEntity.ok(products);
        }

        List<ProductResponseDTO> products = productService.listProductsForUser();
        return ResponseEntity.ok(products);
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

    /**
     * Creates a stock adjustment entry (never edits quantidadeAtual directly via product update).
     * ADMIN applies immediately, USER creates pending validation.
     */
    @PostMapping("/{id}/stock-adjustments")
    public ResponseEntity<ActionResponseDTO> adjustStock(
            @PathVariable UUID id,
            @Valid @RequestBody StockAdjustmentDTO dto,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        UUID userId = requireUserId(authenticatedUser);
        UserRole role = requireRole(authenticatedUser);

        logger.info("POST /api/v1/products/{}/stock-adjustments by user {} ({})", id, userId, role);

        ActionResponseDTO response = productService.adjustStock(id, dto, role, userId);
        return toRoleAwareResponse(response);
    }

    @GetMapping("/{id}/images")
    public ResponseEntity<List<ProductImageMetadataDTO>> listProductImages(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        requireUserId(authenticatedUser);
        return ResponseEntity.ok(productImageService.listImages(id));
    }

    @GetMapping("/{productId}/images/{imageId}/content")
    public ResponseEntity<ByteArrayResource> getProductImageContent(
            @PathVariable UUID productId,
            @PathVariable UUID imageId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        requireUserId(authenticatedUser);

        ProductImage image = productImageService.getImageContent(productId, imageId);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(image.getContentType());
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        String fileName = image.getFileName() != null ? image.getFileName() : image.getId() + ".bin";
        ByteArrayResource resource = new ByteArrayResource(image.getImageData());

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(image.getImageData().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .body(resource);
    }

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ActionResponseDTO> uploadProductImage(
            @PathVariable UUID id,
            @RequestPart("image") MultipartFile image,
            @RequestParam(name = "isPrimary", required = false, defaultValue = "false") boolean isPrimary,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        UUID userId = requireUserId(authenticatedUser);
        UserRole role = requireRole(authenticatedUser);

        ActionResponseDTO response = productImageService.uploadImage(id, image, isPrimary, role, userId);
        return toRoleAwareResponse(response);
    }

    @PatchMapping("/{productId}/images/{imageId}/primary")
    public ResponseEntity<ActionResponseDTO> setPrimaryImage(
            @PathVariable UUID productId,
            @PathVariable UUID imageId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        UUID userId = requireUserId(authenticatedUser);
        UserRole role = requireRole(authenticatedUser);

        ActionResponseDTO response = productImageService.setPrimaryImage(productId, imageId, role, userId);
        return toRoleAwareResponse(response);
    }

    @DeleteMapping("/{productId}/images/{imageId}")
    public ResponseEntity<ActionResponseDTO> deleteImage(
            @PathVariable UUID productId,
            @PathVariable UUID imageId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        UUID userId = requireUserId(authenticatedUser);
        UserRole role = requireRole(authenticatedUser);

        ActionResponseDTO response = productImageService.deleteImage(productId, imageId, role, userId);
        return toRoleAwareResponse(response);
    }

    private UserRole requireRole(AuthenticatedUser authenticatedUser) {
        requireUserId(authenticatedUser);
        return UserRole.valueOf(authenticatedUser.getRole().toUpperCase(Locale.ROOT));
    }

    private UUID requireUserId(AuthenticatedUser authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.getId() == null) {
            throw new AuthenticationCredentialsNotFoundException("Authenticated user not found");
        }
        return authenticatedUser.getId();
    }

    private ResponseEntity<ActionResponseDTO> toRoleAwareResponse(ActionResponseDTO response) {
        if ("UPDATED".equals(response.getStatus())) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
