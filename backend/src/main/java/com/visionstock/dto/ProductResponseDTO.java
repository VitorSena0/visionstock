package com.visionstock.dto;

import com.visionstock.model.inventory.Product;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for the Estoquista (USER role).
 * This DTO intentionally OMITS the {@code precoCusto} field to prevent
 * stockists from viewing cost prices or profit margins.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponseDTO {

    private UUID id;
    private String referencia;
    private String codigoBarras;
    private String imagemUrl;
    private String descricao;
    private String tamanho;
    private String cor;
    private String marca;
    private UUID categoryId;
    private BigDecimal precoVenda;
    private Integer quantidadeAtual;
    private Integer quantidadeMinima;
    private String statusIa;
    private String statusValidacao;
    private String syncStatus;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Converts a Product entity to a ProductResponseDTO.
     * The {@code precoCusto} field is NOT included in this DTO.
     */
    public static ProductResponseDTO fromEntity(Product product) {
        return ProductResponseDTO.builder()
                .id(product.getId())
                .referencia(product.getReferencia())
                .codigoBarras(product.getCodigoBarras())
                .imagemUrl(product.getImagemUrl())
                .descricao(product.getDescricao())
                .tamanho(product.getTamanho())
                .cor(product.getCor())
                .marca(product.getMarca())
                .categoryId(product.getCategoryId())
                .precoVenda(product.getPrecoVenda())
                .quantidadeAtual(product.getQuantidadeAtual())
                .quantidadeMinima(product.getQuantidadeMinima())
                .statusIa(product.getStatusIa())
                .statusValidacao(product.getStatusValidacao())
                .syncStatus(product.getSyncStatus())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
