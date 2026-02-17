package com.visionstock.service;

import com.visionstock.dto.ActionResponseDTO;
import com.visionstock.exception.ResourceNotFoundException;
import com.visionstock.model.enums.UserRole;
import com.visionstock.model.inventory.Product;
import com.visionstock.model.inventory.ProductImage;
import com.visionstock.repository.ProductImageRepository;
import com.visionstock.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductImageServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private ValidationService validationService;

    @InjectMocks
    private ProductImageService productImageService;

    private UUID productId;
    private UUID imageId;
    private UUID adminId;
    private Product product;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        imageId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        product = Product.builder()
                .id(productId)
                .descricao("Produto teste")
                .build();
    }

    @Test
    @DisplayName("setPrimaryImage ADMIN should swap primary atomically and update product image URL")
    void setPrimaryImage_admin_shouldSwapPrimaryAtomically() {
        ProductImage primary = ProductImage.builder()
                .id(imageId)
                .productId(productId)
                .build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productImageRepository.findByIdAndProductIdAndDeletedAtIsNull(imageId, productId))
                .thenReturn(Optional.of(primary));
        when(productImageRepository.setPrimaryByIdAndProductId(eq(imageId), eq(productId), eq(adminId), any()))
                .thenReturn(1);
        when(productImageRepository.findByProductIdAndIsPrimaryTrueAndDeletedAtIsNull(productId))
                .thenReturn(Optional.of(primary));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActionResponseDTO response = productImageService.setPrimaryImage(productId, imageId, UserRole.ADMIN, adminId);

        assertEquals("UPDATED", response.getStatus());
        assertEquals(imageId, response.getResourceId());

        verify(productImageRepository).clearPrimaryByProductId(eq(productId), eq(adminId), any());
        verify(productImageRepository).setPrimaryByIdAndProductId(eq(imageId), eq(productId), eq(adminId), any());

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        assertEquals("/api/v1/products/" + productId + "/images/" + imageId + "/content",
                productCaptor.getValue().getImagemUrl());
    }

    @Test
    @DisplayName("setPrimaryImage ADMIN should throw 404 when target image does not exist")
    void setPrimaryImage_admin_missingImage_shouldThrow() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productImageRepository.findByIdAndProductIdAndDeletedAtIsNull(imageId, productId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> productImageService.setPrimaryImage(productId, imageId, UserRole.ADMIN, adminId));

        verify(productImageRepository, never()).clearPrimaryByProductId(eq(productId), eq(adminId), any());
        verify(productImageRepository, never()).setPrimaryByIdAndProductId(eq(imageId), eq(productId), eq(adminId), any());
    }

    @Test
    @DisplayName("setPrimaryImage ADMIN should throw 404 when atomic update affects zero rows")
    void setPrimaryImage_admin_zeroUpdatedRows_shouldThrow() {
        ProductImage primary = ProductImage.builder()
                .id(imageId)
                .productId(productId)
                .build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productImageRepository.findByIdAndProductIdAndDeletedAtIsNull(imageId, productId))
                .thenReturn(Optional.of(primary));
        when(productImageRepository.setPrimaryByIdAndProductId(eq(imageId), eq(productId), eq(adminId), any()))
                .thenReturn(0);

        assertThrows(ResourceNotFoundException.class,
                () -> productImageService.setPrimaryImage(productId, imageId, UserRole.ADMIN, adminId));

        verify(productImageRepository).clearPrimaryByProductId(eq(productId), eq(adminId), any());
        verify(productRepository, never()).save(any(Product.class));
    }
}

