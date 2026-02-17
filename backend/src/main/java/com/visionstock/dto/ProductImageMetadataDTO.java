package com.visionstock.dto;

import com.visionstock.model.inventory.ProductImage;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImageMetadataDTO {

    private UUID id;
    private UUID productId;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String sha256;
    private Integer width;
    private Integer height;
    private Boolean isPrimary;
    private Integer sortOrder;
    private Instant createdAt;
    private Instant updatedAt;

    public static ProductImageMetadataDTO fromEntity(ProductImage entity) {
        return ProductImageMetadataDTO.builder()
                .id(entity.getId())
                .productId(entity.getProductId())
                .fileName(entity.getFileName())
                .contentType(entity.getContentType())
                .fileSize(entity.getFileSize())
                .sha256(entity.getSha256())
                .width(entity.getWidth())
                .height(entity.getHeight())
                .isPrimary(entity.getIsPrimary())
                .sortOrder(entity.getSortOrder())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
