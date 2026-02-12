package com.visionstock.model.inventory;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products", schema = "inventory")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(length = 100, unique = true)
    private String referencia;

    @Column(name = "codigo_barras", length = 100, unique = true)
    private String codigoBarras;

    @Column(name = "imagem_url")
    private String imagemUrl;

    @Column(nullable = false)
    private String descricao;

    @Column(length = 10)
    private String tamanho;

    @Column(length = 50)
    private String cor;

    @Column(length = 100)
    private String marca;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "preco_custo", precision = 10, scale = 2)
    private BigDecimal precoCusto;

    @Column(name = "preco_venda", nullable = false, precision = 10, scale = 2)
    private BigDecimal precoVenda;

    @Column(name = "quantidade_atual", nullable = false)
    @Builder.Default
    private Integer quantidadeAtual = 0;

    @Column(name = "quantidade_minima")
    @Builder.Default
    private Integer quantidadeMinima = 0;

    @Column(name = "status_ia", length = 50)
    @Builder.Default
    private String statusIa = "MANUAL";

    @Column(name = "status_validacao", length = 20)
    @Builder.Default
    private String statusValidacao = "OK";

    @Column
    @Builder.Default
    private Integer versao = 1;

    @Column(name = "sync_status", length = 20)
    @Builder.Default
    private String syncStatus = "PENDENTE";

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
