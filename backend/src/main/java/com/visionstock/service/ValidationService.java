package com.visionstock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionstock.dto.ProductUpdateDTO;
import com.visionstock.dto.ValidationRequestDTO;
import com.visionstock.exception.ResourceNotFoundException;
import com.visionstock.model.enums.ValidationStatus;
import com.visionstock.model.inventory.Product;
import com.visionstock.model.inventory.ValidationRequest;
import com.visionstock.repository.ProductRepository;
import com.visionstock.repository.ValidationRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing product update validation requests.
 * Handles the approval workflow for product changes requested by estoquistas.
 * 
 * This is the critical "Guard-Rail" that prevents unauthorized changes.
 */
@Service
public class ValidationService {

    private static final Logger logger = LoggerFactory.getLogger(ValidationService.class);

    private final ValidationRequestRepository validationRequestRepository;
    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper;

    public ValidationService(ValidationRequestRepository validationRequestRepository,
                            ProductRepository productRepository,
                            ObjectMapper objectMapper) {
        this.validationRequestRepository = validationRequestRepository;
        this.productRepository = productRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a new validation request (when a USER tries to update a product).
     * The product is NOT updated yet - it's put in the queue for admin approval.
     *
     * @param productId    The product being modified
     * @param updateDTO    The requested changes
     * @param userId       The user (estoquista) requesting the change
     * @return The created ValidationRequest
     */
    @Transactional
    public ValidationRequest createValidationRequest(UUID productId, ProductUpdateDTO updateDTO, UUID userId) {
        logger.info("Creating validation request for product {} by user {}", productId, userId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));

        // Serialize current product state as "before" snapshot
        String originalDataJson = serializeProductSnapshot(product);

        // Create a temporary product copy to serialize the "after" state
        Product updatedCopy = createUpdatedProductCopy(product, updateDTO);
        String newDataJson = serializeProductSnapshot(updatedCopy);

        // Create and save the validation request
        ValidationRequest request = ValidationRequest.builder()
                .id(UUID.randomUUID())
                .product(product)
                .productId(productId)
                .requestedBy(userId)
                .status(ValidationStatus.PENDING)
                .originalData(originalDataJson)
                .newData(newDataJson)
                .build();

        validationRequestRepository.save(request);
        logger.info("Validation request created with ID: {} (status: PENDING)", request.getId());

        return request;
    }

    /**
     * Lists all pending validation requests (the approval queue).
     * Only available to ADMIN users.
     *
     * @return List of pending validation requests
     */
    @Transactional(readOnly = true)
    public List<ValidationRequestDTO> getPendingRequests() {
        List<ValidationRequest> requests = validationRequestRepository
                .findByStatusOrderByRequestedAtAsc(ValidationStatus.PENDING);
        
        logger.info("Retrieved {} pending validation requests", requests.size());
        
        return requests.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Lists all validation requests for a specific product.
     *
     * @param productId The product ID
     * @return List of validation requests for the product
     */
    @Transactional(readOnly = true)
    public List<ValidationRequestDTO> getRequestsByProduct(UUID productId) {
        List<ValidationRequest> requests = validationRequestRepository
                .findByProductIdOrderByRequestedAtDesc(productId);
        
        return requests.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Approves a validation request and applies the changes to the product.
     * ATOMIC: If product update fails, the entire transaction rolls back.
     *
     * @param requestId   The ID of the validation request to approve
     * @param adminId     The UUID of the admin approving this request
     * @param reviewNote  Optional note explaining the approval
     * @return The updated Product
     */
    @Transactional
    public Product approveRequest(UUID requestId, UUID adminId, String reviewNote) {
        logger.info("Approving validation request {} by admin {}", requestId, adminId);

        ValidationRequest request = validationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Validation request not found with ID: " + requestId));

        if (!request.isPending()) {
            throw new IllegalStateException(
                    "Cannot approve request with status: " + request.getStatus());
        }

        // Extract the new data and apply it to the product
        JsonNode newDataNode;
        try {
            newDataNode = objectMapper.readTree(request.getNewData());
        } catch (Exception e) {
            logger.error("Error parsing new data JSON for validation request {}", requestId, e);
            throw new RuntimeException("Failed to parse validation request data", e);
        }
        Product product = request.getProduct();

        // Apply changes to the product entity
        applyChangesToProduct(product, newDataNode);
        product.setUpdatedBy(adminId);

        // Save the updated product
        product = productRepository.save(product);
        logger.info("Product {} updated with approved changes", product.getId());

        // Mark the request as approved
        request.approve(adminId);
        request.setReviewNote(reviewNote);
        validationRequestRepository.save(request);
        logger.info("Validation request {} marked as APPROVED", requestId);

        return product;
    }

    /**
     * Rejects a validation request without applying any changes to the product.
     * The product remains unchanged.
     *
     * @param requestId   The ID of the validation request to reject
     * @param adminId     The UUID of the admin rejecting this request
     * @param reviewNote  Optional note explaining the rejection reason
     * @return The unchanged Product
     */
    @Transactional
    public Product rejectRequest(UUID requestId, UUID adminId, String reviewNote) {
        logger.info("Rejecting validation request {} by admin {}", requestId, adminId);

        ValidationRequest request = validationRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Validation request not found with ID: " + requestId));

        if (!request.isPending()) {
            throw new IllegalStateException(
                    "Cannot reject request with status: " + request.getStatus());
        }

        // Mark the request as rejected
        request.reject(adminId);
        request.setReviewNote(reviewNote);
        validationRequestRepository.save(request);
        logger.info("Validation request {} marked as REJECTED", requestId);

        // Return the unchanged product
        return request.getProduct();
    }

    /**
     * Gets statistics about pending validation requests.
     *
     * @return Number of pending requests
     */
    @Transactional(readOnly = true)
    public Long getPendingRequestCount() {
        return validationRequestRepository.countByStatus(ValidationStatus.PENDING);
    }

    // ==================== PRIVATE HELPER METHODS ====================

    /**
     * Serializes a product to JSON string containing only the editable fields.
     */
    private String serializeProductSnapshot(Product product) {
        try {
            return objectMapper.writeValueAsString(
                    objectMapper.createObjectNode()
                            .put("id", product.getId().toString())
                            .put("descricao", product.getDescricao())
                            .put("cor", product.getCor())
                            .put("tamanho", product.getTamanho())
                            .put("precoVenda", product.getPrecoVenda().toPlainString())
            );
        } catch (Exception e) {
            logger.error("Error serializing product snapshot", e);
            throw new RuntimeException("Failed to serialize product data", e);
        }
    }

    /**
     * Creates a temporary copy of the product with the requested updates applied.
     * This is used to generate the "new data" snapshot for comparison.
     */
    private Product createUpdatedProductCopy(Product original, ProductUpdateDTO updateDTO) {
        Product copy = Product.builder()
                .id(original.getId())
                .descricao(updateDTO.getDescricao() != null ? updateDTO.getDescricao() : original.getDescricao())
                .cor(updateDTO.getCor() != null ? updateDTO.getCor() : original.getCor())
                .tamanho(updateDTO.getTamanho() != null ? updateDTO.getTamanho() : original.getTamanho())
                .precoVenda(updateDTO.getPrecoVenda() != null ? updateDTO.getPrecoVenda() : original.getPrecoVenda())
                .build();

        // Copy all other fields to maintain reference
        copy.setReferencia(original.getReferencia());
        copy.setCodigoBarras(original.getCodigoBarras());
        copy.setImagemUrl(original.getImagemUrl());
        copy.setMarca(original.getMarca());
        copy.setCategoryId(original.getCategoryId());
        copy.setPrecoCusto(original.getPrecoCusto());
        copy.setQuantidadeAtual(original.getQuantidadeAtual());
        copy.setQuantidadeMinima(original.getQuantidadeMinima());
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

    /**
     * Applies the approved changes from a validation request to a product entity.
     * Only updates the editable fields: descricao, cor, tamanho, precoVenda.
     */
    private void applyChangesToProduct(Product product, JsonNode dataNode) {
        try {
            if (dataNode.has("descricao") && !dataNode.get("descricao").isNull()) {
                product.setDescricao(dataNode.get("descricao").asText());
            }
            if (dataNode.has("cor") && !dataNode.get("cor").isNull()) {
                product.setCor(dataNode.get("cor").asText());
            }
            if (dataNode.has("tamanho") && !dataNode.get("tamanho").isNull()) {
                product.setTamanho(dataNode.get("tamanho").asText());
            }
            if (dataNode.has("precoVenda") && !dataNode.get("precoVenda").isNull()) {
                product.setPrecoVenda(new BigDecimal(dataNode.get("precoVenda").asText()));
            }
        } catch (Exception e) {
            logger.error("Error applying changes to product", e);
            throw new RuntimeException("Failed to apply product changes", e);
        }
    }

    /**
     * Converts a ValidationRequest entity to a DTO for API responses.
     */
    private ValidationRequestDTO convertToDTO(ValidationRequest request) {
        JsonNode originalNode;
        JsonNode newNode;
        try {
            originalNode = objectMapper.readTree(request.getOriginalData());
            newNode = objectMapper.readTree(request.getNewData());
            String changesSummary = generateChangesSummary(originalNode, newNode);

            return ValidationRequestDTO.builder()
                    .id(request.getId())
                    .productId(request.getProductId())
                    .productReferencia(request.getProduct().getReferencia())
                    .productDescricao(request.getProduct().getDescricao())
                    .requestedBy(request.getRequestedBy())
                    .status(request.getStatus())
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

    /**
     * Generates a human-readable summary of what changed.
     * Example: "Descrição alterada, Preço aumentado"
     */
    private String generateChangesSummary(JsonNode original, JsonNode newNode) {
        List<String> changes = new ArrayList<>();

        if (!original.get("descricao").asText().equals(newNode.get("descricao").asText())) {
            changes.add("Descrição alterada");
        }
        if (!original.get("cor").asText().equals(newNode.get("cor").asText())) {
            changes.add("Cor alterada");
        }
        if (!original.get("tamanho").asText().equals(newNode.get("tamanho").asText())) {
            changes.add("Tamanho alterado");
        }
        if (!original.get("precoVenda").asText().equals(newNode.get("precoVenda").asText())) {
            changes.add("Preço alterado");
        }

        return String.join(", ", changes);
    }
}
