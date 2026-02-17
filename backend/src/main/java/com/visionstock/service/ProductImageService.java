package com.visionstock.service;

import com.visionstock.dto.ActionResponseDTO;
import com.visionstock.dto.ProductImageMetadataDTO;
import com.visionstock.exception.ResourceNotFoundException;
import com.visionstock.model.enums.ImageOperationType;
import com.visionstock.model.enums.UserRole;
import com.visionstock.model.inventory.Product;
import com.visionstock.model.inventory.ProductImage;
import com.visionstock.model.inventory.ValidationRequest;
import com.visionstock.repository.ProductImageRepository;
import com.visionstock.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;

@Service
public class ProductImageService {

    private static final Logger logger = LoggerFactory.getLogger(ProductImageService.class);
    private static final long MAX_IMAGE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/heic",
            "image/heif"
    );

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ValidationService validationService;

    public ProductImageService(ProductRepository productRepository,
                               ProductImageRepository productImageRepository,
                               ValidationService validationService) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.validationService = validationService;
    }

    @Transactional(readOnly = true)
    public List<ProductImageMetadataDTO> listImages(UUID productId) {
        ensureProductExists(productId);
        return productImageRepository.findByProductIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(productId)
                .stream()
                .map(ProductImageMetadataDTO::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductImage getImageContent(UUID productId, UUID imageId) {
        ensureProductExists(productId);
        return productImageRepository.findByIdAndProductIdAndDeletedAtIsNull(imageId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found for product"));
    }

    @Transactional
    public ActionResponseDTO uploadImage(
            UUID productId,
            MultipartFile file,
            boolean isPrimary,
            UserRole role,
            UUID userId) {
        ensureProductExists(productId);
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Image file is empty");
        }
        validateImageFile(file);

        try {
            byte[] imageBytes = file.getBytes();
            String contentType = normalizeContentType(file.getContentType());
            String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "image.jpg";
            Long fileSize = file.getSize();
            String sha256 = calculateSha256(imageBytes);
            int[] dimensions = readImageDimensions(imageBytes);
            Integer width = dimensions[0] > 0 ? dimensions[0] : null;
            Integer height = dimensions[1] > 0 ? dimensions[1] : null;

            if (role.isAdmin()) {
                ProductImage saved = saveImageDirect(
                        productId,
                        imageBytes,
                        fileName,
                        contentType,
                        fileSize,
                        isPrimary,
                        sha256,
                        width,
                        height,
                        userId);

                return ActionResponseDTO.builder()
                        .success(true)
                        .status("UPDATED")
                        .message("Imagem anexada com sucesso")
                        .resourceId(saved.getId())
                        .build();
            }

            ValidationRequest request = validationService.createImageValidationRequest(
                    productId,
                    ImageOperationType.ADD,
                    null,
                    imageBytes,
                    fileName,
                    contentType,
                    fileSize,
                    isPrimary,
                    sha256,
                    width,
                    height,
                    userId);

            return ActionResponseDTO.builder()
                    .success(true)
                    .status("PENDING_APPROVAL")
                    .message("Imagem enviada para aprovação do gerente")
                    .validationRequestId(request.getId())
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read image bytes", e);
        }
    }

    @Transactional
    public ActionResponseDTO setPrimaryImage(UUID productId, UUID imageId, UserRole role, UUID userId) {
        ensureProductExists(productId);
        logger.info("Setting primary image. productId={}, imageId={}, role={}, userId={}",
                productId, imageId, role, userId);

        if (role.isAdmin()) {
            setPrimaryDirect(productId, imageId, userId, role);
            logger.info("Primary image updated immediately for product {} (ADMIN flow)", productId);
            return ActionResponseDTO.builder()
                    .success(true)
                    .status("UPDATED")
                    .message("Imagem principal atualizada")
                    .resourceId(imageId)
                    .build();
        }

        ValidationRequest request = validationService.createImageValidationRequest(
                productId,
                ImageOperationType.SET_PRIMARY,
                imageId,
                null,
                null,
                null,
                null,
                true,
                null,
                null,
                null,
                userId);

        logger.info("Primary image change queued for approval. productId={}, imageId={}, validationRequestId={}",
                productId, imageId, request.getId());

        return ActionResponseDTO.builder()
                .success(true)
                .status("PENDING_APPROVAL")
                .message("Alteração de imagem enviada para aprovação")
                .validationRequestId(request.getId())
                .resourceId(imageId)
                .build();
    }

    @Transactional
    public ActionResponseDTO deleteImage(UUID productId, UUID imageId, UserRole role, UUID userId) {
        ensureProductExists(productId);
        if (role.isAdmin()) {
            deleteImageDirect(productId, imageId, userId);
            return ActionResponseDTO.builder()
                    .success(true)
                    .status("UPDATED")
                    .message("Imagem removida com sucesso")
                    .resourceId(imageId)
                    .build();
        }

        ValidationRequest request = validationService.createImageValidationRequest(
                productId,
                ImageOperationType.DELETE,
                imageId,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                userId);

        return ActionResponseDTO.builder()
                .success(true)
                .status("PENDING_APPROVAL")
                .message("Remoção de imagem enviada para aprovação")
                .validationRequestId(request.getId())
                .resourceId(imageId)
                .build();
    }

    private ProductImage saveImageDirect(
            UUID productId,
            byte[] imageBytes,
            String fileName,
            String contentType,
            Long fileSize,
            boolean isPrimary,
            String sha256,
            Integer width,
            Integer height,
            UUID userId) {
        // Serialize image writes per product to avoid unique-primary races.
        ensureProductExistsForUpdate(productId);

        Optional<ProductImage> duplicateByHash = Optional.empty();
        if (sha256 != null && !sha256.isBlank()) {
            Optional<ProductImage> candidate =
                    productImageRepository.findFirstByProductIdAndSha256AndDeletedAtIsNullOrderByCreatedAtAsc(
                            productId,
                            sha256);
            if (candidate != null) {
                duplicateByHash = candidate;
            }
        }

        if (duplicateByHash.isPresent()) {
            ProductImage existingImage = duplicateByHash.get();
            if (isPrimary && !Boolean.TRUE.equals(existingImage.getIsPrimary())) {
                setPrimaryDirect(productId, existingImage.getId(), userId, UserRole.ADMIN);
            } else {
                setProductPrimaryImageUrl(productId, userId);
            }
            return existingImage;
        }

        long existingCount = productImageRepository.countByProductIdAndDeletedAtIsNull(productId);
        boolean shouldBePrimary = isPrimary || existingCount == 0;
        Instant now = Instant.now();
        if (shouldBePrimary) {
            productImageRepository.clearPrimaryByProductId(productId, userId, now);
        }

        ProductImage entity = ProductImage.builder()
                .id(UUID.randomUUID())
                .productId(productId)
                .fileName(fileName)
                .contentType(contentType)
                .fileSize(fileSize)
                .imageData(imageBytes)
                .sha256(sha256)
                .width(width)
                .height(height)
                .isPrimary(shouldBePrimary)
                .sortOrder((int) existingCount)
                .createdBy(userId)
                .updatedBy(userId)
                .build();

        ProductImage saved = productImageRepository.saveAndFlush(entity);
        setProductPrimaryImageUrl(productId, userId);
        return saved;
    }

    private void setPrimaryDirect(UUID productId, UUID imageId, UUID userId, UserRole role) {
        ensureProductExistsForUpdate(productId);

        productImageRepository.findByIdAndProductIdAndDeletedAtIsNull(imageId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found for product"));

        Instant now = Instant.now();
        int clearedRows = productImageRepository.clearPrimaryByProductId(productId, userId, now);
        int updatedRows = productImageRepository.setPrimaryByIdAndProductId(imageId, productId, userId, now);

        logger.info("Primary image atomic update result. productId={}, imageId={}, userId={}, role={}, clearedRows={}, updatedRows={}",
                productId, imageId, userId, role, clearedRows, updatedRows);

        if (updatedRows == 0) {
            throw new ResourceNotFoundException("Image not found for product");
        }

        setProductPrimaryImageUrl(productId, userId);
    }

    private void deleteImageDirect(UUID productId, UUID imageId, UUID userId) {
        ensureProductExistsForUpdate(productId);

        ProductImage image = productImageRepository.findByIdAndProductIdAndDeletedAtIsNull(imageId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found for product"));

        boolean wasPrimary = Boolean.TRUE.equals(image.getIsPrimary());
        image.setDeletedAt(Instant.now());
        image.setIsPrimary(false);
        image.setUpdatedBy(userId);
        productImageRepository.save(image);

        if (wasPrimary) {
            ProductImage nextPrimary = productImageRepository
                    .findByProductIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(productId)
                    .stream()
                    .findFirst()
                    .orElse(null);

            Instant now = Instant.now();
            productImageRepository.clearPrimaryByProductId(productId, userId, now);

            if (nextPrimary != null) {
                productImageRepository.setPrimaryByIdAndProductId(
                        nextPrimary.getId(),
                        productId,
                        userId,
                        now);
            }
        }

        setProductPrimaryImageUrl(productId, userId);
    }

    private void setProductPrimaryImageUrl(UUID productId, UUID userId) {
        Product product = ensureProductExists(productId);
        ProductImage primary = productImageRepository.findByProductIdAndIsPrimaryTrueAndDeletedAtIsNull(productId)
                .orElse(null);

        product.setImagemUrl(primary != null
                ? "/api/v1/products/" + productId + "/images/" + primary.getId() + "/content"
                : null);
        product.setUpdatedBy(userId);
        productRepository.save(product);
    }

    private Product ensureProductExists(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));
    }

    private Product ensureProductExistsForUpdate(UUID productId) {
        Optional<Product> locked = productRepository.findByIdForUpdate(productId);
        if (locked != null && locked.isPresent()) {
            return locked.get();
        }
        return ensureProductExists(productId);
    }

    private void validateImageFile(MultipartFile file) {
        long fileSize = file.getSize();
        if (fileSize <= 0) {
            throw new IllegalArgumentException("Image file is empty");
        }
        if (fileSize > MAX_IMAGE_SIZE_BYTES) {
            throw new IllegalArgumentException("Image exceeds max size of 5MB");
        }

        String contentType = normalizeContentType(file.getContentType());
        if (!SUPPORTED_IMAGE_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Unsupported image type. Allowed: image/jpeg, image/jpg, image/png, image/webp, image/heic, image/heif");
        }
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "image/jpeg";
        }
        return contentType.toLowerCase(Locale.ROOT).trim();
    }

    private String calculateSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private int[] readImageDimensions(byte[] imageData) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(imageData)) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                return new int[]{0, 0};
            }
            return new int[]{image.getWidth(), image.getHeight()};
        } catch (Exception e) {
            logger.debug("Could not infer image dimensions", e);
            return new int[]{0, 0};
        }
    }
}
