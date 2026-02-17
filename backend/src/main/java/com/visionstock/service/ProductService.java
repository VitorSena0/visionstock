package com.visionstock.service;

import com.visionstock.dto.ProductCreateDTO;
import com.visionstock.dto.ProductAdminDTO;
import com.visionstock.dto.ProductResponseDTO;
import com.visionstock.dto.ProductUpdateDTO;
import com.visionstock.dto.StockAdjustmentDTO;
import com.visionstock.dto.ActionResponseDTO;
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
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);
    private static final BigDecimal MAX_MARKUP_PERCENT = new BigDecimal("99999999.99");

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
    public ProductResponseDTO createProduct(ProductCreateDTO dto, UUID createdBy) {
        logger.info("Creating product with ID: {}", dto.getId());
        String referencia = normalizeText(dto.getReferencia());
        String codigoBarras = normalizeText(dto.getCodigoBarras());

        // 1. Check for duplicate by ID
        if (productRepository.existsById(dto.getId())) {
            throw new DuplicateProductException(
                    "Product with ID " + dto.getId() + " already exists");
        }

        // 2. Check for duplicate by reference (if provided)
        if (referencia != null && productRepository.existsByReferencia(referencia)) {
            throw new DuplicateProductException(
                    "Product with reference " + referencia + " already exists");
        }

        // 3. Check for duplicate by barcode (if provided)
        if (codigoBarras != null && productRepository.existsByCodigoBarras(codigoBarras)) {
                throw new DuplicateProductException(
                        "Product with barcode " + codigoBarras + " already exists");
        }

        validateMarkupRange(dto.getPrecoCusto(), dto.getPrecoVenda());

        // 4. Build and save Product entity
        Product product = Product.builder()
                .id(dto.getId())
                .referencia(referencia)
                .descricao(dto.getDescricao())
                .tamanho(dto.getTamanho())
                .cor(dto.getCor())
                .marca(dto.getMarca())
                .codigoBarras(codigoBarras)
                .precoCusto(dto.getPrecoCusto())
                .precoVenda(dto.getPrecoVenda())
                .quantidadeAtual(dto.getQuantidadeInicial() != null ? dto.getQuantidadeInicial() : 0)
                .quantidadeMinima(dto.getQuantidadeMinima() != null ? dto.getQuantidadeMinima() : 0)
                .statusIa("MANUAL")
                .statusValidacao("OK")
                .createdBy(createdBy)
                .updatedBy(createdBy)
                .build();

        product = productRepository.saveAndFlush(product);

        // 5. Create initial stock movement if quantidadeInicial > 0 AND a user is provided
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
            logger.info("Product created with initial quantity {} but no authenticated user was provided. No stock movement was created.",
                    dto.getQuantidadeInicial());
        }

        logger.info("Product created successfully: {}", product.getId());
        return ProductResponseDTO.fromEntity(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDTO> listProductsForUser() {
        return productRepository.findByDeletedAtIsNullOrderByUpdatedAtDesc()
                .stream()
                .map(ProductResponseDTO::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductAdminDTO> listProductsForAdmin() {
        return productRepository.findByDeletedAtIsNullOrderByUpdatedAtDesc()
                .stream()
                .map(ProductAdminDTO::fromEntity)
                .toList();
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
        if (dto.getReferencia() != null && !dto.getReferencia().isBlank()) {
            String referencia = normalizeText(dto.getReferencia());
            if (referencia != null && productRepository.existsByReferenciaAndIdNot(referencia, product.getId())) {
                throw new DuplicateProductException(
                        "Product with reference " + referencia + " already exists");
            }
            product.setReferencia(referencia);
        }
        if (dto.getCodigoBarras() != null && !dto.getCodigoBarras().isBlank()) {
            String codigoBarras = normalizeText(dto.getCodigoBarras());
            if (codigoBarras != null && productRepository.existsByCodigoBarrasAndIdNot(codigoBarras, product.getId())) {
                throw new DuplicateProductException(
                        "Product with barcode " + codigoBarras + " already exists");
            }
            product.setCodigoBarras(codigoBarras);
        }
        if (dto.getDescricao() != null && !dto.getDescricao().isBlank()) {
            product.setDescricao(dto.getDescricao());
        }
        if (dto.getCor() != null && !dto.getCor().isBlank()) {
            product.setCor(dto.getCor());
        }
        if (dto.getTamanho() != null && !dto.getTamanho().isBlank()) {
            product.setTamanho(dto.getTamanho());
        }
        if (dto.getMarca() != null && !dto.getMarca().isBlank()) {
            product.setMarca(dto.getMarca());
        }
        if (dto.getPrecoCusto() != null) {
            product.setPrecoCusto(dto.getPrecoCusto());
        }
        if (dto.getPrecoVenda() != null) {
            product.setPrecoVenda(dto.getPrecoVenda());
        }
        if (dto.getQuantidadeMinima() != null) {
            product.setQuantidadeMinima(dto.getQuantidadeMinima());
        }

        validateMarkupRange(product.getPrecoCusto(), product.getPrecoVenda());

        product.setUpdatedBy(adminId);
        product = productRepository.saveAndFlush(product);

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

    @Transactional
    public ActionResponseDTO adjustStock(UUID productId, StockAdjustmentDTO dto, UserRole role, UUID userId) {
        logger.info("Stock adjustment request for product {} by user {} with role {}", productId, userId, role);

        if (dto.getQuantidadeDelta() == null || dto.getQuantidadeDelta() == 0) {
            throw new IllegalArgumentException("quantidadeDelta must be different from zero");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));

        if (role.isAdmin()) {
            applyStockAdjustment(product, dto.getQuantidadeDelta(), dto.getMotivo(), userId);
            return ActionResponseDTO.builder()
                    .success(true)
                    .status("UPDATED")
                    .message("Ajuste de estoque aplicado com sucesso")
                    .resourceId(productId)
                    .build();
        }

        com.visionstock.model.inventory.ValidationRequest request =
                validationService.createStockAdjustmentValidationRequest(productId, dto, userId);

        return ActionResponseDTO.builder()
                .success(true)
                .status("PENDING_APPROVAL")
                .message("Ajuste enviado para aprovação do gerente")
                .validationRequestId(request.getId())
                .resourceId(productId)
                .build();
    }

    private void applyStockAdjustment(Product product, int quantidadeDelta, String motivo, UUID userId) {
        int quantidadeAtual = product.getQuantidadeAtual() != null ? product.getQuantidadeAtual() : 0;
        int novaQuantidade = quantidadeAtual + quantidadeDelta;
        if (novaQuantidade < 0) {
            throw new IllegalArgumentException("Stock adjustment would result in negative quantity");
        }

        product.setQuantidadeAtual(novaQuantidade);
        product.setUpdatedBy(userId);
        productRepository.save(product);

        StockMovement movement = StockMovement.builder()
                .id(UUID.randomUUID())
                .productId(product.getId())
                .userId(userId)
                .tipoMovimento(MovementType.AJUSTE.name())
                .quantidade(quantidadeDelta)
                .valorUnitario(product.getPrecoCusto() != null ? product.getPrecoCusto() : BigDecimal.ZERO)
                .observacao(motivo != null && !motivo.isBlank()
                        ? "Ajuste de estoque: " + motivo
                        : "Ajuste de estoque")
                .build();

        stockMovementRepository.save(movement);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void validateMarkupRange(BigDecimal precoCusto, BigDecimal precoVenda) {
        if (precoCusto == null || precoVenda == null || precoCusto.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal markup = precoVenda
                .subtract(precoCusto)
                .divide(precoCusto, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        if (markup.abs().compareTo(MAX_MARKUP_PERCENT) > 0) {
            throw new IllegalArgumentException(
                    "Markup percentual excede o limite permitido para os valores de custo e venda informados");
        }
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
