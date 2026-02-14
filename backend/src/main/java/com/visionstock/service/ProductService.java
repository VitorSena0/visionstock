package com.visionstock.service;

import com.visionstock.dto.ProductCreateDTO;
import com.visionstock.dto.ProductResponseDTO;
import com.visionstock.dto.ProductUpdateDTO;
import com.visionstock.exception.DuplicateProductException;
import com.visionstock.exception.ResourceNotFoundException;
import com.visionstock.model.enums.MovementType;
import com.visionstock.model.enums.UserRole;
import com.visionstock.model.finance.StockMovement;
import com.visionstock.model.inventory.Product;
import com.visionstock.repository.ProductRepository;
import com.visionstock.repository.StockMovementRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class ProductService {

    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ValidationService validationService;

    public ProductService(ProductRepository productRepository,
                          StockMovementRepository stockMovementRepository,
                          ValidationService validationService) {
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.validationService = validationService;
    }

    /**
     * Creates a new product with initial stock movement if quantidadeInicial > 0.
     * This operation is transactional.
     */
    @Transactional
    public ProductResponseDTO createProduct(ProductCreateDTO dto) {
        logger.info("Creating product with ID: {}", dto.getId());

        // 1. Check for duplicate by ID
        if (productRepository.existsById(dto.getId())) {
            throw new DuplicateProductException(
                    "Product with ID " + dto.getId() + " already exists");
        }

        // 2. Check for duplicate by barcode (if provided)
        if (dto.getCodigoBarras() != null && !dto.getCodigoBarras().isBlank()) {
            if (productRepository.existsByCodigoBarras(dto.getCodigoBarras())) {
                throw new DuplicateProductException(
                        "Product with barcode " + dto.getCodigoBarras() + " already exists");
            }
        }

        // 3. Build and save Product entity
        Product product = Product.builder()
                .id(dto.getId())
                .referencia(dto.getReferencia())
                .descricao(dto.getDescricao())
                .tamanho(dto.getTamanho())
                .cor(dto.getCor())
                .marca(dto.getMarca())
                .codigoBarras(dto.getCodigoBarras())
                .precoCusto(dto.getPrecoCusto())
                .precoVenda(dto.getPrecoVenda())
                .quantidadeAtual(dto.getQuantidadeInicial() != null ? dto.getQuantidadeInicial() : 0)
                .statusIa("MANUAL")
                .statusValidacao("OK")
                .build();

        product = productRepository.save(product);

        // 4. Create initial stock movement if quantidadeInicial > 0 AND a user is provided
        // In offline-first scenarios, if no user is provided, the movement will be created later during sync
        if (dto.getQuantidadeInicial() != null && dto.getQuantidadeInicial() > 0 && product.getCreatedBy() != null) {
            StockMovement movement = StockMovement.builder()
                    .id(UUID.randomUUID())
                    .productId(product.getId())
                    .userId(product.getCreatedBy())
                    .tipoMovimento(MovementType.ENTRADA.name())
                    .quantidade(dto.getQuantidadeInicial())
                    .valorUnitario(dto.getPrecoCusto() != null ? dto.getPrecoCusto() : BigDecimal.ZERO)
                    .observacao("Entrada inicial - cadastro de produto")
                    .build();

            stockMovementRepository.save(movement);
            logger.info("Initial stock movement created for product {}: {} units",
                    product.getId(), dto.getQuantidadeInicial());
        } else if (dto.getQuantidadeInicial() != null && dto.getQuantidadeInicial() > 0) {
            logger.info("Product created with initial quantity {} but no user provided. Stock movement will be created during sync.",
                    dto.getQuantidadeInicial());
        }

        logger.info("Product created successfully: {}", product.getId());
        return ProductResponseDTO.fromEntity(product);
    }

    /**
     * Updates a product following the approval workflow pattern.
     * 
     * Strategy Pattern Implementation:
     * - If role == ADMIN: Updates product directly in the database
     * - If role == USER: Creates a ValidationRequest for admin approval
     *
     * @param id       The product ID to update
     * @param dto      The update request containing new values
     * @param role     The user's role (ADMIN or USER)
     * @param userId   The ID of the user making the request
     * @return Response indicating whether the change was applied or pending approval
     */
    @Transactional
    public ProductUpdateResponse updateProduct(UUID id, ProductUpdateDTO dto, UserRole role, UUID userId) {
        logger.info("Update request for product {} by user {} with role {}", id, userId, role);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));

        if (role.isAdmin()) {
            // ADMIN PATH: Apply changes directly
            logger.info("ADMIN update - applying changes directly to product {}", id);
            return updateProductDirect(product, dto, userId);
        } else {
            // USER (ESTOQUISTA) PATH: Create validation request
            logger.info("USER update - creating validation request for product {}", id);
            return updateProductWithValidation(product, dto, userId);
        }
    }

    /**
     * Direct update for ADMIN users.
     * Changes are applied immediately to the database.
     */
    private ProductUpdateResponse updateProductDirect(Product product, ProductUpdateDTO dto, UUID adminId) {
        if (dto.getDescricao() != null && !dto.getDescricao().isBlank()) {
            product.setDescricao(dto.getDescricao());
        }
        if (dto.getCor() != null && !dto.getCor().isBlank()) {
            product.setCor(dto.getCor());
        }
        if (dto.getTamanho() != null && !dto.getTamanho().isBlank()) {
            product.setTamanho(dto.getTamanho());
        }
        if (dto.getPrecoVenda() != null) {
            product.setPrecoVenda(dto.getPrecoVenda());
        }

        product.setUpdatedBy(adminId);
        product = productRepository.save(product);

        logger.info("Product {} updated successfully by ADMIN {}", product.getId(), adminId);

        return ProductUpdateResponse.builder()
                .success(true)
                .status("UPDATED")
                .message("Produto atualizado com sucesso")
                .product(ProductResponseDTO.fromEntity(product))
                .build();
    }

    /**
     * Update with validation for USER (estoquista) users.
     * Creates a ValidationRequest and returns a pending status.
     */
    private ProductUpdateResponse updateProductWithValidation(Product product, ProductUpdateDTO dto, UUID userId) {
        com.visionstock.model.inventory.ValidationRequest validationRequest = 
                validationService.createValidationRequest(product.getId(), dto, userId);

        logger.info("Validation request created {} for product {}", validationRequest.getId(), product.getId());

        return ProductUpdateResponse.builder()
                .success(true)
                .status("PENDING_APPROVAL")
                .message("Alteração enviada para aprovação do gerente")
                .validationRequestId(validationRequest.getId())
                .product(ProductResponseDTO.fromEntity(product))
                .build();
    }

    /**
     * Response DTO for product update operations.
     * Indicates whether the update was applied immediately or is pending approval.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductUpdateResponse {
        private boolean success;
        private String status;  // UPDATED or PENDING_APPROVAL
        private String message;
        private UUID validationRequestId;  // Only set when status = PENDING_APPROVAL
        private ProductResponseDTO product;
    }
}
