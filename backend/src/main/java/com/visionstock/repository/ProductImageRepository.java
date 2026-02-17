package com.visionstock.repository;

import com.visionstock.model.inventory.ProductImage;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    List<ProductImage> findByProductIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(UUID productId);

    Optional<ProductImage> findByIdAndProductIdAndDeletedAtIsNull(UUID id, UUID productId);

    Optional<ProductImage> findByProductIdAndIsPrimaryTrueAndDeletedAtIsNull(UUID productId);

    Optional<ProductImage> findFirstByProductIdAndSha256AndDeletedAtIsNullOrderByCreatedAtAsc(
            UUID productId,
            String sha256);

    long countByProductIdAndDeletedAtIsNull(UUID productId);

    @Modifying
    @Query("""
            UPDATE ProductImage pi
            SET pi.isPrimary = false,
                pi.updatedBy = :userId,
                pi.updatedAt = :updatedAt
            WHERE pi.productId = :productId
              AND pi.deletedAt IS NULL
            """)
    int clearPrimaryByProductId(@Param("productId") UUID productId,
                                @Param("userId") UUID userId,
                                @Param("updatedAt") Instant updatedAt);

    @Modifying
    @Query("""
            UPDATE ProductImage pi
            SET pi.isPrimary = true,
                pi.updatedBy = :userId,
                pi.updatedAt = :updatedAt
            WHERE pi.id = :imageId
              AND pi.productId = :productId
              AND pi.deletedAt IS NULL
            """)
    int setPrimaryByIdAndProductId(@Param("imageId") UUID imageId,
                                   @Param("productId") UUID productId,
                                   @Param("userId") UUID userId,
                                   @Param("updatedAt") Instant updatedAt);
}
