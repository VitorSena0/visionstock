package com.visionstock.dto;

import com.visionstock.model.inventory.Product;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for the Gerente (ADMIN role).
 * This DTO includes ALL fields, including sensitive financial data
 * such as {@code precoCusto} and calculated {@code markupPercentual}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductAdminDTO {

    private UUID id;
    private String referencia;
    private String codigoBarras;
    private String imagemUrl;
    private String descricao;
    private String tamanho;
    private String cor;
    private String marca;
    private UUID categoryId;
    private BigDecimal precoCusto;
    private BigDecimal precoVenda;
    private BigDecimal markupPercentual;
    private Integer quantidadeAtual;
    private Integer quantidadeMinima;
    private String statusIa;
    private String statusValidacao;
    private Integer versao;
    private String syncStatus;
    private Instant createdAt;
    private Instant updatedAt;
    private UUID createdBy;
    private UUID updatedBy;

    /**
     * Converts a Product entity to a ProductAdminDTO, including all financial data.
     * The {@code markupPercentual} is calculated from {@code precoCusto} and {@code precoVenda}.
     */
    public static ProductAdminDTO fromEntity(Product product) {
        BigDecimal markup = null;
        if (product.getPrecoVenda() != null
                && product.getPrecoCusto() != null
                && product.getPrecoCusto().compareTo(BigDecimal.ZERO) > 0) {
            markup = product.getPrecoVenda()
                    .subtract(product.getPrecoCusto())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(product.getPrecoCusto(), 2, java.math.RoundingMode.HALF_UP);
        }

        return ProductAdminDTO.builder()
                .id(product.getId())
                .referencia(product.getReferencia())
                .codigoBarras(product.getCodigoBarras())
                .imagemUrl(product.getImagemUrl())
                .descricao(product.getDescricao())
                .tamanho(product.getTamanho())
                .cor(product.getCor())
                .marca(product.getMarca())
                .categoryId(product.getCategoryId())
                .precoCusto(product.getPrecoCusto())
                .precoVenda(product.getPrecoVenda())
                .markupPercentual(markup)
                .quantidadeAtual(product.getQuantidadeAtual())
                .quantidadeMinima(product.getQuantidadeMinima())
                .statusIa(product.getStatusIa())
                .statusValidacao(product.getStatusValidacao())
                .versao(product.getVersao())
                .syncStatus(product.getSyncStatus())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .createdBy(product.getCreatedBy())
                .updatedBy(product.getUpdatedBy())
                .build();
    }
}
