package com.visionstock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionstock.dto.ProductUpdateDTO;
import com.visionstock.dto.StockAdjustmentDTO;
import com.visionstock.dto.ValidationRequestDTO;
import com.visionstock.exception.ResourceNotFoundException;
import com.visionstock.model.enums.ImageOperationType;
import com.visionstock.model.enums.MovementType;
import com.visionstock.model.enums.ValidationChangeType;
import com.visionstock.model.enums.ValidationStatus;
import com.visionstock.model.finance.StockMovement;
import com.visionstock.model.inventory.Product;
import com.visionstock.model.inventory.ProductImage;
import com.visionstock.model.inventory.ValidationImageStaging;
import com.visionstock.model.inventory.ValidationRequest;
import com.visionstock.repository.ProductImageRepository;
import com.visionstock.repository.ProductRepository;
import com.visionstock.repository.StockMovementRepository;
import com.visionstock.repository.ValidationImageStagingRepository;
import com.visionstock.repository.ValidationRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ValidationService {

    private static final Logger logger = LoggerFactory.getLogger(ValidationService.class);
    private static final BigDecimal MAX_MARKUP_PERCENT = new BigDecimal("99999999.99");

    private final ValidationRequestRepository validationRequestRepository;
    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ProductImageRepository productImageRepository;
    private final ValidationImageStagingRepository validationImageStagingRepository;
    private final ObjectMapper objectMapper;

    public ValidationService(ValidationRequestRepository validationRequestRepository,
                             ProductRepository productRepository,
                             StockMovementRepository stockMovementRepository,
                             ProductImageRepository productImageRepository,
                             ValidationImageStagingRepository validationImageStagingRepository,
                             ObjectMapper objectMapper) {
        this.validationRequestRepository = validationRequestRepository;
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.productImageRepository = productImageRepository;
        this.validationImageStagingRepository = validationImageStagingRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ValidationRequest createValidationRequest(UUID productId, ProductUpdateDTO updateDTO, UUID userId) {
        logger.info("Creating product field validation request for product {} by user {}", productId, userId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));

        String originalDataJson = serializeProductSnapshot(product);
        Product updatedCopy = createUpdatedProductCopy(product, updateDTO);
        String newDataJson = serializeProductSnapshot(updatedCopy);

        ValidationRequest request = ValidationRequest.builder()
                .id(UUID.randomUUID())
                .product(product)
                .productId(productId)
                .requestedBy(userId)
                .status(ValidationStatus.PENDING)
                .changeType(ValidationChangeType.PRODUCT_FIELDS)
                .originalData(originalDataJson)
                .newData(newDataJson)
                .build();

        validationRequestRepository.save(request);
        logger.info("Validation request created with ID: {} (type: PRODUCT_FIELDS)", request.getId());
        return request;
    }

    @Transactional
    public ValidationRequest createStockAdjustmentValidationRequest(
            UUID productId,
            StockAdjustmentDTO adjustmentDTO,
            UUID userId) {
        logger.info("Creating stock adjustment validation request for product {} by user {}", productId, userId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));

        if (adjustmentDTO.getQuantidadeDelta() == null || adjustmentDTO.getQuantidadeDelta() == 0) {
            throw new IllegalArgumentException("quantidadeDelta must be different from zero");
        }

        String originalDataJson = serializeStockSnapshot(product);
        String newDataJson = serializeStockAdjustmentRequest(adjustmentDTO);

        ValidationRequest request = ValidationRequest.builder()
                .id(UUID.randomUUID())
                .product(product)
                .productId(productId)
                .requestedBy(userId)
                .status(ValidationStatus.PENDING)
                .changeType(ValidationChangeType.STOCK_ADJUSTMENT)
                .originalData(originalDataJson)
                .newData(newDataJson)
                .build();

        validationRequestRepository.save(request);
        logger.info("Validation request created with ID: {} (type: STOCK_ADJUSTMENT)", request.getId());
        return request;
    }

    @Transactional
    public ValidationRequest createImageValidationRequest(
            UUID productId,
            ImageOperationType operation,
            UUID targetImageId,
            byte[] imageData,
            String fileName,
            String contentType,
            Long fileSize,
            Boolean isPrimary,
            String sha256,
            Integer width,
            Integer height,
            UUID userId) {
        logger.info("Creating image validation request for product {} by user {} (operation={})",
                productId, userId, operation);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));

        String originalDataJson = serializeImageSnapshot(productId);
        String newDataJson = serializeImageRequest(operation, targetImageId, fileName, contentType, fileSize, isPrimary, sha256);

        ValidationRequest request = ValidationRequest.builder()
                .id(UUID.randomUUID())
                .product(product)
                .productId(productId)
                .requestedBy(userId)
                .status(ValidationStatus.PENDING)
                .changeType(ValidationChangeType.PRODUCT_IMAGES)
                .originalData(originalDataJson)
                .newData(newDataJson)
                .build();

        validationRequestRepository.save(request);

        if (imageData != null && imageData.length > 0) {
            ValidationImageStaging staging = ValidationImageStaging.builder()
                    .id(UUID.randomUUID())
                    .validationRequestId(request.getId())
                    .operation(operation)
                    .targetImageId(targetImageId)
                    .fileName(fileName)
                    .contentType(contentType)
                    .fileSize(fileSize)
                    .imageData(imageData)
                    .sha256(sha256)
                    .width(width)
                    .height(height)
                    .isPrimary(isPrimary)
                    .createdBy(userId)
                    .updatedBy(userId)
                    .build();
            validationImageStagingRepository.save(staging);
        }

        logger.info("Validation request created with ID: {} (type: PRODUCT_IMAGES)", request.getId());
        return request;
    }

    @Transactional(readOnly = true)
    public List<ValidationRequestDTO> getPendingRequests() {
        List<ValidationRequest> requests = validationRequestRepository
                .findByStatusOrderByRequestedAtAsc(ValidationStatus.PENDING);

        logger.info("Retrieved {} pending validation requests", requests.size());

        return requests.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ValidationRequestDTO> getRequestsByProduct(UUID productId) {
        List<ValidationRequest> requests = validationRequestRepository
                .findByProductIdOrderByRequestedAtDesc(productId);

        return requests.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ValidationRequestDTO> getRequestsByRequester(UUID requestedBy) {
        List<ValidationRequest> requests = validationRequestRepository
                .findByRequestedByOrderByRequestedAtDesc(requestedBy);

        return requests.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public Product approveRequest(UUID requestId, UUID adminId, String reviewNote) {
        logger.info("Approving validation request {} by admin {}", requestId, adminId);

        ValidationRequest request = validationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Validation request not found with ID: " + requestId));

        if (!request.isPending()) {
            throw new IllegalStateException("Cannot approve request with status: " + request.getStatus());
        }

        JsonNode newDataNode;
        try {
            newDataNode = objectMapper.readTree(request.getNewData());
        } catch (Exception e) {
            logger.error("Error parsing new data JSON for validation request {}", requestId, e);
            throw new RuntimeException("Failed to parse validation request data", e);
        }

        Product product = request.getProduct();

        if (request.getChangeType() == ValidationChangeType.STOCK_ADJUSTMENT) {
            applyStockAdjustmentFromValidation(product, newDataNode, adminId);
        } else if (request.getChangeType() == ValidationChangeType.PRODUCT_IMAGES) {
            applyImageChangesFromValidation(request, newDataNode, adminId);
            product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found after applying image changes"));
        } else {
            applyChangesToProduct(product, newDataNode);
            product.setUpdatedBy(adminId);
            product = productRepository.save(product);
        }

        request.approve(adminId);
        request.setReviewNote(reviewNote);
        validationRequestRepository.save(request);
        logger.info("Validation request {} marked as APPROVED", requestId);

        return product;
    }

    @Transactional
    public Product rejectRequest(UUID requestId, UUID adminId, String reviewNote) {
        logger.info("Rejecting validation request {} by admin {}", requestId, adminId);

        ValidationRequest request = validationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Validation request not found with ID: " + requestId));

        if (!request.isPending()) {
            throw new IllegalStateException("Cannot reject request with status: " + request.getStatus());
        }

        if (request.getChangeType() == ValidationChangeType.PRODUCT_IMAGES) {
            List<ValidationImageStaging> stagingEntries =
                    validationImageStagingRepository.findByValidationRequestIdAndDeletedAtIsNullOrderByCreatedAtAsc(requestId);
            Instant now = Instant.now();
            for (ValidationImageStaging staging : stagingEntries) {
                staging.setDeletedAt(now);
                staging.setUpdatedBy(adminId);
                validationImageStagingRepository.save(staging);
            }
        }

        request.reject(adminId);
        request.setReviewNote(reviewNote);
        validationRequestRepository.save(request);
        logger.info("Validation request {} marked as REJECTED", requestId);

        return request.getProduct();
    }

    @Transactional(readOnly = true)
    public Long getPendingRequestCount() {
        return validationRequestRepository.countByStatus(ValidationStatus.PENDING);
    }

    private String serializeProductSnapshot(Product product) {
        try {
            return objectMapper.writeValueAsString(
                    objectMapper.createObjectNode()
                            .put("id", product.getId().toString())
                            .put("referencia", nullableString(product.getReferencia()))
                            .put("codigoBarras", nullableString(product.getCodigoBarras()))
                            .put("descricao", nullableString(product.getDescricao()))
                            .put("cor", nullableString(product.getCor()))
                            .put("tamanho", nullableString(product.getTamanho()))
                            .put("marca", nullableString(product.getMarca()))
                            .put("precoCusto", product.getPrecoCusto() != null ? product.getPrecoCusto().toPlainString() : "")
                            .put("precoVenda", product.getPrecoVenda() != null ? product.getPrecoVenda().toPlainString() : "")
                            .put("quantidadeMinima", product.getQuantidadeMinima() != null ? product.getQuantidadeMinima() : 0)
            );
        } catch (Exception e) {
            logger.error("Error serializing product snapshot", e);
            throw new RuntimeException("Failed to serialize product data", e);
        }
    }

    private String serializeStockSnapshot(Product product) {
        try {
            return objectMapper.writeValueAsString(
                    objectMapper.createObjectNode()
                            .put("id", product.getId().toString())
                            .put("quantidadeAtual", product.getQuantidadeAtual() != null ? product.getQuantidadeAtual() : 0)
            );
        } catch (Exception e) {
            logger.error("Error serializing stock snapshot", e);
            throw new RuntimeException("Failed to serialize stock snapshot", e);
        }
    }

    private String serializeStockAdjustmentRequest(StockAdjustmentDTO dto) {
        try {
            return objectMapper.writeValueAsString(
                    objectMapper.createObjectNode()
                            .put("quantidadeDelta", dto.getQuantidadeDelta())
                            .put("motivo", nullableString(dto.getMotivo()))
            );
        } catch (Exception e) {
            logger.error("Error serializing stock adjustment request", e);
            throw new RuntimeException("Failed to serialize stock adjustment request", e);
        }
    }

    private String serializeImageSnapshot(UUID productId) {
        try {
            List<ProductImage> images = productImageRepository.findByProductIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(productId);
            var node = objectMapper.createObjectNode();
            var arrayNode = objectMapper.createArrayNode();
            for (ProductImage image : images) {
                arrayNode.add(objectMapper.createObjectNode()
                        .put("id", image.getId().toString())
                        .put("isPrimary", Boolean.TRUE.equals(image.getIsPrimary())));
            }
            node.set("images", arrayNode);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            logger.error("Error serializing image snapshot", e);
            throw new RuntimeException("Failed to serialize image snapshot", e);
        }
    }

    private String serializeImageRequest(
            ImageOperationType operation,
            UUID targetImageId,
            String fileName,
            String contentType,
            Long fileSize,
            Boolean isPrimary,
            String sha256) {
        try {
            return objectMapper.writeValueAsString(
                    objectMapper.createObjectNode()
                            .put("operation", operation.name())
                            .put("targetImageId", targetImageId != null ? targetImageId.toString() : "")
                            .put("fileName", nullableString(fileName))
                            .put("contentType", nullableString(contentType))
                            .put("fileSize", fileSize != null ? fileSize : 0)
                            .put("isPrimary", isPrimary != null && isPrimary)
                            .put("sha256", nullableString(sha256))
            );
        } catch (Exception e) {
            logger.error("Error serializing image request", e);
            throw new RuntimeException("Failed to serialize image request", e);
        }
    }

    private Product createUpdatedProductCopy(Product original, ProductUpdateDTO updateDTO) {
        Product copy = Product.builder()
                .id(original.getId())
                .referencia(updateDTO.getReferencia() != null ? updateDTO.getReferencia() : original.getReferencia())
                .codigoBarras(updateDTO.getCodigoBarras() != null ? updateDTO.getCodigoBarras() : original.getCodigoBarras())
                .descricao(updateDTO.getDescricao() != null ? updateDTO.getDescricao() : original.getDescricao())
                .cor(updateDTO.getCor() != null ? updateDTO.getCor() : original.getCor())
                .tamanho(updateDTO.getTamanho() != null ? updateDTO.getTamanho() : original.getTamanho())
                .marca(updateDTO.getMarca() != null ? updateDTO.getMarca() : original.getMarca())
                .precoCusto(updateDTO.getPrecoCusto() != null ? updateDTO.getPrecoCusto() : original.getPrecoCusto())
                .precoVenda(updateDTO.getPrecoVenda() != null ? updateDTO.getPrecoVenda() : original.getPrecoVenda())
                .quantidadeMinima(updateDTO.getQuantidadeMinima() != null ? updateDTO.getQuantidadeMinima() : original.getQuantidadeMinima())
                .build();

        copy.setImagemUrl(original.getImagemUrl());
        copy.setCategoryId(original.getCategoryId());
        copy.setQuantidadeAtual(original.getQuantidadeAtual());
        copy.setStatusIa(original.getStatusIa());
        copy.setStatusValidacao(original.getStatusValidacao());
        copy.setVersao(original.getVersao());
        copy.setSyncStatus(original.getSyncStatus());
        copy.setCreatedAt(original.getCreatedAt());
        copy.setUpdatedAt(original.getUpdatedAt());
        copy.setDeletedAt(original.getDeletedAt());
        copy.setCreatedBy(original.getCreatedBy());
        copy.setUpdatedBy(original.getUpdatedBy());

        return copy;
    }

    private void applyChangesToProduct(Product product, JsonNode dataNode) {
        try {
            applyStringField(dataNode, "referencia", product::setReferencia);
            applyStringField(dataNode, "codigoBarras", product::setCodigoBarras);
            applyStringField(dataNode, "descricao", product::setDescricao);
            applyStringField(dataNode, "cor", product::setCor);
            applyStringField(dataNode, "tamanho", product::setTamanho);
            applyStringField(dataNode, "marca", product::setMarca);

            if (dataNode.has("precoCusto") && !dataNode.get("precoCusto").asText().isBlank()) {
                product.setPrecoCusto(new BigDecimal(dataNode.get("precoCusto").asText()));
            }
            if (dataNode.has("precoVenda") && !dataNode.get("precoVenda").asText().isBlank()) {
                product.setPrecoVenda(new BigDecimal(dataNode.get("precoVenda").asText()));
            }
            if (dataNode.has("quantidadeMinima")) {
                product.setQuantidadeMinima(dataNode.get("quantidadeMinima").asInt());
            }
            validateMarkupRange(product.getPrecoCusto(), product.getPrecoVenda());
        } catch (Exception e) {
            logger.error("Error applying changes to product", e);
            throw new RuntimeException("Failed to apply product changes", e);
        }
    }

    private void applyStockAdjustmentFromValidation(Product product, JsonNode dataNode, UUID adminId) {
        int quantidadeDelta = dataNode.path("quantidadeDelta").asInt(0);
        String motivo = dataNode.path("motivo").asText("");
        if (quantidadeDelta == 0) {
            throw new IllegalArgumentException("quantidadeDelta must be different from zero");
        }

        int quantidadeAtual = product.getQuantidadeAtual() != null ? product.getQuantidadeAtual() : 0;
        int novaQuantidade = quantidadeAtual + quantidadeDelta;
        if (novaQuantidade < 0) {
            throw new IllegalArgumentException("Stock adjustment would result in negative quantity");
        }

        product.setQuantidadeAtual(novaQuantidade);
        product.setUpdatedBy(adminId);
        productRepository.save(product);

        StockMovement movement = StockMovement.builder()
                .id(UUID.randomUUID())
                .productId(product.getId())
                .userId(adminId)
                .tipoMovimento(MovementType.AJUSTE.name())
                .quantidade(quantidadeDelta)
                .valorUnitario(product.getPrecoCusto() != null ? product.getPrecoCusto() : BigDecimal.ZERO)
                .observacao(motivo != null && !motivo.isBlank()
                        ? "Ajuste de estoque aprovado: " + motivo
                        : "Ajuste de estoque aprovado")
                .build();
        stockMovementRepository.save(movement);
    }

    private void applyImageChangesFromValidation(ValidationRequest request, JsonNode dataNode, UUID adminId) {
        ImageOperationType operation = ImageOperationType.valueOf(dataNode.path("operation").asText("ADD"));
        UUID productId = request.getProductId();
        lockProductForImageMutation(productId);

        if (operation == ImageOperationType.ADD) {
            List<ValidationImageStaging> stagingEntries =
                    validationImageStagingRepository.findByValidationRequestIdAndDeletedAtIsNullOrderByCreatedAtAsc(request.getId());
            if (stagingEntries.isEmpty()) {
                throw new IllegalStateException("No staging image found for validation request " + request.getId());
            }

            ValidationImageStaging staging = stagingEntries.get(0);
            if (staging.getSha256() != null && !staging.getSha256().isBlank()) {
                var existingByHash = productImageRepository
                        .findFirstByProductIdAndSha256AndDeletedAtIsNullOrderByCreatedAtAsc(productId, staging.getSha256());
                if (existingByHash != null && existingByHash.isPresent()) {
                    ProductImage existingImage = existingByHash.get();
                    if (Boolean.TRUE.equals(staging.getIsPrimary())) {
                        setPrimaryImageAtomic(productId, existingImage.getId(), adminId);
                    } else {
                        setProductPrimaryImageUrl(productId, adminId);
                    }
                    return;
                }
            }

            long currentCount = productImageRepository.countByProductIdAndDeletedAtIsNull(productId);
            boolean shouldBePrimary = Boolean.TRUE.equals(staging.getIsPrimary()) || currentCount == 0;

            ProductImage newImage = ProductImage.builder()
                    .id(UUID.randomUUID())
                    .productId(productId)
                    .fileName(staging.getFileName())
                    .contentType(staging.getContentType() != null ? staging.getContentType() : "image/jpeg")
                    .fileSize(staging.getFileSize() != null ? staging.getFileSize() : 0L)
                    .imageData(staging.getImageData())
                    .sha256(staging.getSha256())
                    .width(staging.getWidth())
                    .height(staging.getHeight())
                    .isPrimary(shouldBePrimary)
                    .sortOrder((int) currentCount)
                    .createdBy(adminId)
                    .updatedBy(adminId)
                    .build();

            if (shouldBePrimary) {
                clearPrimaryImage(productId, adminId);
            }

            productImageRepository.saveAndFlush(newImage);
            setProductPrimaryImageUrl(productId, adminId);
            return;
        }

        String rawTargetImageId = dataNode.path("targetImageId").asText("");
        if (rawTargetImageId.isBlank()) {
            throw new IllegalArgumentException("targetImageId is required for image operation " + operation);
        }
        UUID targetImageId = UUID.fromString(rawTargetImageId);
        ProductImage targetImage = productImageRepository.findByIdAndProductIdAndDeletedAtIsNull(targetImageId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found for product"));

        if (operation == ImageOperationType.SET_PRIMARY) {
            setPrimaryImageAtomic(productId, targetImageId, adminId);
            setProductPrimaryImageUrl(productId, adminId);
            return;
        }

        targetImage.setDeletedAt(Instant.now());
        targetImage.setUpdatedBy(adminId);
        targetImage.setIsPrimary(false);
        productImageRepository.save(targetImage);
        ensurePrimaryImageExists(productId, adminId);
        setProductPrimaryImageUrl(productId, adminId);
    }

    private void clearPrimaryImage(UUID productId, UUID userId) {
        productImageRepository.clearPrimaryByProductId(productId, userId, Instant.now());
    }

    private void setPrimaryImageAtomic(UUID productId, UUID imageId, UUID userId) {
        lockProductForImageMutation(productId);
        Instant now = Instant.now();
        productImageRepository.clearPrimaryByProductId(productId, userId, now);
        int updatedRows = productImageRepository.setPrimaryByIdAndProductId(imageId, productId, userId, now);
        if (updatedRows == 0) {
            throw new ResourceNotFoundException("Image not found for product");
        }
    }

    private void lockProductForImageMutation(UUID productId) {
        var locked = productRepository.findByIdForUpdate(productId);
        if (locked == null || locked.isEmpty()) {
            throw new ResourceNotFoundException("Product not found with ID: " + productId);
        }
    }

    private void ensurePrimaryImageExists(UUID productId, UUID userId) {
        var primary = productImageRepository.findByProductIdAndIsPrimaryTrueAndDeletedAtIsNull(productId);
        if (primary.isPresent()) {
            return;
        }
        List<ProductImage> images = productImageRepository.findByProductIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(productId);
        if (!images.isEmpty()) {
            ProductImage first = images.get(0);
            first.setIsPrimary(true);
            first.setUpdatedBy(userId);
            productImageRepository.save(first);
        }
    }

    private void setProductPrimaryImageUrl(UUID productId, UUID userId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        var primary = productImageRepository.findByProductIdAndIsPrimaryTrueAndDeletedAtIsNull(productId);
        product.setImagemUrl(primary
                .map(productImage -> "/api/v1/products/" + productId + "/images/" + productImage.getId() + "/content")
                .orElse(null));
        product.setUpdatedBy(userId);
        productRepository.save(product);
    }

    private ValidationRequestDTO convertToDTO(ValidationRequest request) {
        JsonNode originalNode;
        JsonNode newNode;
        try {
            originalNode = objectMapper.readTree(request.getOriginalData());
            newNode = objectMapper.readTree(request.getNewData());
            String changesSummary = generateChangesSummary(request.getChangeType(), originalNode, newNode);

            return ValidationRequestDTO.builder()
                    .id(request.getId())
                    .productId(request.getProductId())
                    .productReferencia(request.getProduct().getReferencia())
                    .productDescricao(request.getProduct().getDescricao())
                    .requestedBy(request.getRequestedBy())
                    .status(request.getStatus())
                    .changeType(request.getChangeType())
                    .originalData(originalNode)
                    .newData(newNode)
                    .requestedAt(request.getRequestedAt())
                    .reviewedBy(request.getReviewedBy())
                    .reviewNote(request.getReviewNote())
                    .reviewedAt(request.getReviewedAt())
                    .changesSummary(changesSummary)
                    .build();
        } catch (Exception e) {
            logger.error("Error converting validation request to DTO", e);
            throw new RuntimeException("Failed to convert validation request", e);
        }
    }

    private String generateChangesSummary(ValidationChangeType changeType, JsonNode original, JsonNode newNode) {
        if (changeType == ValidationChangeType.PRODUCT_IMAGES) {
            return "Imagens do produto alteradas";
        }
        if (changeType == ValidationChangeType.STOCK_ADJUSTMENT) {
            return "Ajuste de estoque solicitado";
        }

        List<String> changes = new ArrayList<>();

        compareField(changes, original, newNode, "referencia", "Referencia alterada");
        compareField(changes, original, newNode, "codigoBarras", "Codigo de barras alterado");
        compareField(changes, original, newNode, "descricao", "Descrição alterada");
        compareField(changes, original, newNode, "cor", "Cor alterada");
        compareField(changes, original, newNode, "tamanho", "Tamanho alterado");
        compareField(changes, original, newNode, "marca", "Marca alterada");
        compareField(changes, original, newNode, "precoCusto", "Preço de custo alterado");
        compareField(changes, original, newNode, "precoVenda", "Preço de venda alterado");
        compareField(changes, original, newNode, "quantidadeMinima", "Quantidade mínima alterada");

        return String.join(", ", changes);
    }

    private void compareField(List<String> changes, JsonNode original, JsonNode next, String field, String message) {
        String originalValue = original.path(field).asText("");
        String newValue = next.path(field).asText("");
        if (!originalValue.equals(newValue)) {
            changes.add(message);
        }
    }

    private void applyStringField(JsonNode dataNode, String key, java.util.function.Consumer<String> setter) {
        if (dataNode.has(key) && !dataNode.get(key).asText().isBlank()) {
            setter.accept(dataNode.get(key).asText());
        }
    }

    private String nullableString(String value) {
        return value != null ? value : "";
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
}
