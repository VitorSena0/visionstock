package com.visionstock.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAdjustmentDTO {

    @NotNull(message = "quantidadeDelta is required")
    private Integer quantidadeDelta;

    @Size(max = 255, message = "motivo must not exceed 255 characters")
    private String motivo;
}
