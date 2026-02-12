package com.visionstock.dto;

import com.visionstock.model.inventory.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProductDTOTest {

    private Product product;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .id(UUID.randomUUID())
                .referencia("REF-001")
                .codigoBarras("7891234567890")
                .imagemUrl("https://example.com/img.jpg")
                .descricao("Camiseta Polo Azul")
                .tamanho("M")
                .cor("Azul")
                .marca("Nike")
                .categoryId(UUID.randomUUID())
                .precoCusto(new BigDecimal("45.00"))
                .precoVenda(new BigDecimal("89.90"))
                .quantidadeAtual(50)
                .quantidadeMinima(10)
                .statusIa("MANUAL")
                .statusValidacao("OK")
                .versao(1)
                .syncStatus("PENDENTE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy(UUID.randomUUID())
                .updatedBy(UUID.randomUUID())
                .build();
    }

    @Test
    @DisplayName("ProductResponseDTO must NOT contain precoCusto field")
    void productResponseDTO_shouldOmitPrecoCusto() {
        ProductResponseDTO dto = ProductResponseDTO.fromEntity(product);

        assertEquals(product.getId(), dto.getId());
        assertEquals(product.getReferencia(), dto.getReferencia());
        assertEquals(product.getDescricao(), dto.getDescricao());
        assertEquals(product.getPrecoVenda(), dto.getPrecoVenda());
        assertEquals(product.getQuantidadeAtual(), dto.getQuantidadeAtual());

        // Verify that ProductResponseDTO class has NO precoCusto field
        assertThrows(NoSuchFieldException.class, () ->
                ProductResponseDTO.class.getDeclaredField("precoCusto"),
                "ProductResponseDTO MUST NOT have a precoCusto field for security reasons"
        );
    }

    @Test
    @DisplayName("ProductAdminDTO must contain precoCusto and markupPercentual")
    void productAdminDTO_shouldIncludeFinancialData() {
        ProductAdminDTO dto = ProductAdminDTO.fromEntity(product);

        assertEquals(product.getId(), dto.getId());
        assertEquals(product.getPrecoCusto(), dto.getPrecoCusto());
        assertEquals(product.getPrecoVenda(), dto.getPrecoVenda());
        assertNotNull(dto.getMarkupPercentual());
        assertEquals(product.getVersao(), dto.getVersao());
        assertEquals(product.getCreatedBy(), dto.getCreatedBy());
        assertEquals(product.getUpdatedBy(), dto.getUpdatedBy());
    }

    @Test
    @DisplayName("ProductAdminDTO must calculate markup correctly")
    void productAdminDTO_shouldCalculateMarkupCorrectly() {
        // markup = ((89.90 - 45.00) / 45.00) * 100 = 99.78%
        ProductAdminDTO dto = ProductAdminDTO.fromEntity(product);

        BigDecimal expectedMarkup = new BigDecimal("99.78");
        assertEquals(0, expectedMarkup.compareTo(dto.getMarkupPercentual()),
                "Markup should be ((89.90 - 45.00) / 45.00) * 100 = 99.78%");
    }

    @Test
    @DisplayName("ProductAdminDTO markup should be null when precoCusto is null")
    void productAdminDTO_markupNullWhenCostIsNull() {
        product.setPrecoCusto(null);
        ProductAdminDTO dto = ProductAdminDTO.fromEntity(product);

        assertNull(dto.getMarkupPercentual());
    }

    @Test
    @DisplayName("ProductAdminDTO markup should be null when precoCusto is zero")
    void productAdminDTO_markupNullWhenCostIsZero() {
        product.setPrecoCusto(BigDecimal.ZERO);
        ProductAdminDTO dto = ProductAdminDTO.fromEntity(product);

        assertNull(dto.getMarkupPercentual());
    }

    @Test
    @DisplayName("ProductResponseDTO should map all non-sensitive fields correctly")
    void productResponseDTO_shouldMapAllFields() {
        ProductResponseDTO dto = ProductResponseDTO.fromEntity(product);

        assertEquals(product.getId(), dto.getId());
        assertEquals(product.getReferencia(), dto.getReferencia());
        assertEquals(product.getCodigoBarras(), dto.getCodigoBarras());
        assertEquals(product.getImagemUrl(), dto.getImagemUrl());
        assertEquals(product.getDescricao(), dto.getDescricao());
        assertEquals(product.getTamanho(), dto.getTamanho());
        assertEquals(product.getCor(), dto.getCor());
        assertEquals(product.getMarca(), dto.getMarca());
        assertEquals(product.getCategoryId(), dto.getCategoryId());
        assertEquals(product.getPrecoVenda(), dto.getPrecoVenda());
        assertEquals(product.getQuantidadeAtual(), dto.getQuantidadeAtual());
        assertEquals(product.getQuantidadeMinima(), dto.getQuantidadeMinima());
        assertEquals(product.getStatusIa(), dto.getStatusIa());
        assertEquals(product.getStatusValidacao(), dto.getStatusValidacao());
        assertEquals(product.getSyncStatus(), dto.getSyncStatus());
        assertEquals(product.getCreatedAt(), dto.getCreatedAt());
        assertEquals(product.getUpdatedAt(), dto.getUpdatedAt());
    }

    @Test
    @DisplayName("Product ID must be UUID type")
    void product_idShouldBeUUID() {
        assertNotNull(product.getId());
        assertInstanceOf(UUID.class, product.getId());
    }

    @Test
    @DisplayName("Product monetary values must be BigDecimal")
    void product_monetaryValuesShouldBeBigDecimal() {
        assertInstanceOf(BigDecimal.class, product.getPrecoCusto());
        assertInstanceOf(BigDecimal.class, product.getPrecoVenda());
    }
}
