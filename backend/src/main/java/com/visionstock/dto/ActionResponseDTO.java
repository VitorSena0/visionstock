package com.visionstock.dto;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionResponseDTO {

    private boolean success;
    private String status;
    private String message;
    private UUID validationRequestId;
    private UUID resourceId;
}
