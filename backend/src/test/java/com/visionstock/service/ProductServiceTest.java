package com.visionstock.service;

import com.visionstock.dto.ProductCreateDTO;
import com.visionstock.dto.ProductResponseDTO;
import com.visionstock.exception.DuplicateProductException;
import com.visionstock.model.finance.StockMovement;
import com.visionstock.model.inventory.Product;
import com.visionstock.repository.ProductRepository;
import com.visionstock.repository.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private ValidationService validationService;

    @InjectMocks
    private ProductService productService;

    private ProductCreateDTO validDTO;
    private UUID productId;
    private UUID authenticatedUserId;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        authenticatedUserId = UUID.randomUUID();
        validDTO = ProductCreateDTO.builder()
                .id(productId)
                .referencia("REF-001")
                .descricao("Camiseta Polo Azul")
                .tamanho("M")
                .cor("Azul")
                .marca("Nike")
                .codigoBarras("7891234567890")
                .precoCusto(new BigDecimal("45.00"))
                .precoVenda(new BigDecimal("89.90"))
                .quantidadeInicial(10)
                .build();
    }

    @Test
    @DisplayName("createProduct should save product and return ProductResponseDTO")
    void createProduct_shouldSaveAndReturnDTO() {
        when(productRepository.existsById(productId)).thenReturn(false);
        when(productRepository.existsByReferencia("REF-001")).thenReturn(false);
        when(productRepository.existsByCodigoBarras("7891234567890")).thenReturn(false);
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponseDTO result = productService.createProduct(validDTO, authenticatedUserId);

        assertNotNull(result);
        assertEquals(productId, result.getId());
        assertEquals("Camiseta Polo Azul", result.getDescricao());
        assertEquals("MANUAL", result.getStatusIa());
        assertEquals("OK", result.getStatusValidacao());
        assertEquals(10, result.getQuantidadeAtual());

        verify(productRepository).saveAndFlush(any(Product.class));
    }

    @Test
    @DisplayName("createProduct should create stock movement when quantidadeInicial > 0 AND createdBy is present")
    void createProduct_shouldCreateStockMovement() {
        UUID userId = UUID.randomUUID();
        when(productRepository.existsById(productId)).thenReturn(false);
        when(productRepository.existsByReferencia("REF-001")).thenReturn(false);
        when(productRepository.existsByCodigoBarras("7891234567890")).thenReturn(false);
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.createProduct(validDTO, userId);

        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(captor.capture());

        StockMovement movement = captor.getValue();
        assertEquals(productId, movement.getProductId());
        assertEquals("ENTRADA", movement.getTipoMovimento());
        assertEquals(10, movement.getQuantidade());
        assertEquals(new BigDecimal("45.00"), movement.getValorUnitario());
        assertEquals(userId, movement.getUserId());
    }

    @Test
    @DisplayName("createProduct should NOT create stock movement when authenticated user is null")
    void createProduct_noCreatedBy_shouldNotCreateMovement() {
        when(productRepository.existsById(productId)).thenReturn(false);
        when(productRepository.existsByReferencia("REF-001")).thenReturn(false);
        when(productRepository.existsByCodigoBarras("7891234567890")).thenReturn(false);
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.createProduct(validDTO, null);

        verify(stockMovementRepository, never()).save(any(StockMovement.class));
    }

    @Test
    @DisplayName("createProduct should NOT create stock movement when quantidadeInicial is 0")
    void createProduct_zeroQuantity_shouldNotCreateMovement() {
        validDTO.setQuantidadeInicial(0);
        when(productRepository.existsById(productId)).thenReturn(false);
        when(productRepository.existsByReferencia("REF-001")).thenReturn(false);
        when(productRepository.existsByCodigoBarras("7891234567890")).thenReturn(false);
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.createProduct(validDTO, authenticatedUserId);

        verify(stockMovementRepository, never()).save(any(StockMovement.class));
    }

    @Test
    @DisplayName("createProduct should throw DuplicateProductException when ID already exists")
    void createProduct_duplicateId_shouldThrow() {
        when(productRepository.existsById(productId)).thenReturn(true);

        assertThrows(DuplicateProductException.class,
                () -> productService.createProduct(validDTO, authenticatedUserId));

        verify(productRepository, never()).saveAndFlush(any(Product.class));
    }

    @Test
    @DisplayName("createProduct should throw DuplicateProductException when barcode already exists")
    void createProduct_duplicateBarcode_shouldThrow() {
        when(productRepository.existsById(productId)).thenReturn(false);
        when(productRepository.existsByReferencia("REF-001")).thenReturn(false);
        when(productRepository.existsByCodigoBarras("7891234567890")).thenReturn(true);

        assertThrows(DuplicateProductException.class,
                () -> productService.createProduct(validDTO, authenticatedUserId));

        verify(productRepository, never()).saveAndFlush(any(Product.class));
    }

    @Test
    @DisplayName("createProduct should throw DuplicateProductException when reference already exists")
    void createProduct_duplicateReference_shouldThrow() {
        when(productRepository.existsById(productId)).thenReturn(false);
        when(productRepository.existsByReferencia("REF-001")).thenReturn(true);

        assertThrows(DuplicateProductException.class,
                () -> productService.createProduct(validDTO, authenticatedUserId));

        verify(productRepository, never()).saveAndFlush(any(Product.class));
        verify(productRepository, never()).existsByCodigoBarras(any());
    }

    @Test
    @DisplayName("createProduct should set statusIa to MANUAL")
    void createProduct_shouldSetStatusIaManual() {
        when(productRepository.existsById(productId)).thenReturn(false);
        when(productRepository.existsByReferencia("REF-001")).thenReturn(false);
        when(productRepository.existsByCodigoBarras("7891234567890")).thenReturn(false);
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponseDTO result = productService.createProduct(validDTO, authenticatedUserId);

        assertEquals("MANUAL", result.getStatusIa());
    }

    @Test
    @DisplayName("createProduct should accept frontend-generated UUID (offline-first)")
    void createProduct_shouldUseProvidedUUID() {
        UUID frontendUUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
        validDTO.setId(frontendUUID);

        when(productRepository.existsById(frontendUUID)).thenReturn(false);
        when(productRepository.existsByReferencia("REF-001")).thenReturn(false);
        when(productRepository.existsByCodigoBarras("7891234567890")).thenReturn(false);
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponseDTO result = productService.createProduct(validDTO, authenticatedUserId);

        assertEquals(frontendUUID, result.getId());

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).saveAndFlush(captor.capture());
        assertEquals(frontendUUID, captor.getValue().getId());
    }

    @Test
    @DisplayName("createProduct should use BigDecimal.ZERO as valorUnitario when precoCusto is null")
    void createProduct_nullPrecoCusto_shouldUseZeroForMovement() {
        UUID userId = UUID.randomUUID();
        validDTO.setPrecoCusto(null);
        when(productRepository.existsById(productId)).thenReturn(false);
        when(productRepository.existsByReferencia("REF-001")).thenReturn(false);
        when(productRepository.existsByCodigoBarras("7891234567890")).thenReturn(false);
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.createProduct(validDTO, userId);

        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(captor.capture());
        assertEquals(BigDecimal.ZERO, captor.getValue().getValorUnitario());
    }

    @Test
    @DisplayName("createProduct should skip barcode check when codigoBarras is null")
    void createProduct_nullBarcode_shouldSkipBarcodeCheck() {
        validDTO.setCodigoBarras(null);
        when(productRepository.existsById(productId)).thenReturn(false);
        when(productRepository.existsByReferencia("REF-001")).thenReturn(false);
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.createProduct(validDTO, authenticatedUserId);

        verify(productRepository, never()).existsByCodigoBarras(any());
        verify(productRepository).saveAndFlush(any(Product.class));
    }
}
