package com.visionstock.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

/**
 * DTO for updating a Product.
 * Contains only the editable fields that a USER (Estoquista) can modify.
 * 
 * When an Estoquista submits this DTO:
 * - If user is ADMIN: Product is updated immediately
 * - If user is USER: A ValidationRequest is created for admin approval
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductUpdateDTO {

    @Size(max = 100, message = "Referencia must not exceed 100 characters")
    private String referencia;

    @Size(max = 100, message = "Codigo de barras must not exceed 100 characters")
    private String codigoBarras;

    @Size(min = 3, max = 500, message = "Descrição must be between 3 and 500 characters")
    private String descricao;

    @Size(max = 50, message = "Cor must not exceed 50 characters")
    private String cor;

    @Size(max = 10, message = "Tamanho must not exceed 10 characters")
    private String tamanho;

    @Size(max = 100, message = "Marca must not exceed 100 characters")
    private String marca;

    @DecimalMin(value = "0.0", inclusive = true, message = "Preço de custo must be at least 0")
    @DecimalMax(value = "99999999.99", inclusive = true, message = "Preço de custo exceeds maximum allowed value")
    @Digits(integer = 8, fraction = 2, message = "Preço de custo must have up to 8 integer digits and 2 decimal places")
    private BigDecimal precoCusto;

    @DecimalMin(value = "0.0", inclusive = true, message = "Preço de venda must be at least 0")
    @DecimalMax(value = "99999999.99", inclusive = true, message = "Preço de venda exceeds maximum allowed value")
    @Digits(integer = 8, fraction = 2, message = "Preço de venda must have up to 8 integer digits and 2 decimal places")
    private BigDecimal precoVenda;

    @Min(value = 0, message = "Quantidade minima must be at least 0")
    private Integer quantidadeMinima;

    /**
     * Optional note explaining why the changes are being requested.
     */
    @Size(max = 500, message = "Note must not exceed 500 characters")
    private String nota;
}
