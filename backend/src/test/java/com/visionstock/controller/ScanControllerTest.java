package com.visionstock.controller;

import com.visionstock.dto.ProductResponseDTO;
import com.visionstock.service.GeminiService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScanController.class)
class ScanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GeminiService geminiService;

    @Test
    @DisplayName("POST /api/v1/scan should return product data from AI")
    void scanImage_shouldReturnProductData() throws Exception {
        ProductResponseDTO mockDTO = ProductResponseDTO.builder()
                .descricao("Camiseta Polo Azul")
                .tamanho("M")
                .cor("Azul")
                .marca("Nike")
                .codigoBarras("7891234567890")
                .precoVenda(new BigDecimal("89.90"))
                .statusIa("IA_SUGERIDO")
                .statusValidacao("PENDENTE")
                .build();

        when(geminiService.extractDataFromImage(any())).thenReturn(mockDTO);
        // O arquivo da imagem tem que ficar com o nome "image" para o controller reconhecer, então ficará no diretório de teste mesmo, e não em resources
        MockMultipartFile imageFile = new MockMultipartFile(
                "image", "label.jpg", "image/jpeg", "fake-image-data".getBytes());

        mockMvc.perform(multipart("/api/v1/scan").file(imageFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao").value("Camiseta Polo Azul"))
                .andExpect(jsonPath("$.tamanho").value("M"))
                .andExpect(jsonPath("$.cor").value("Azul"))
                .andExpect(jsonPath("$.marca").value("Nike"))
                .andExpect(jsonPath("$.codigoBarras").value("7891234567890"))
                .andExpect(jsonPath("$.precoVenda").value(89.90))
                .andExpect(jsonPath("$.statusIa").value("IA_SUGERIDO"))
                .andExpect(jsonPath("$.statusValidacao").value("PENDENTE"));
    }

    @Test
    @DisplayName("POST /api/v1/scan with empty file should return 400")
    void scanImage_emptyFile_shouldReturn400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "image", "empty.jpg", "image/jpeg", new byte[0]);

        mockMvc.perform(multipart("/api/v1/scan").file(emptyFile))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/scan with AI error should return error DTO")
    void scanImage_aiError_shouldReturnErrorDTO() throws Exception {
        ProductResponseDTO errorDTO = ProductResponseDTO.builder()
                .statusIa("ERRO_IA")
                .statusValidacao("PENDENTE")
                .build();

        when(geminiService.extractDataFromImage(any())).thenReturn(errorDTO);

        MockMultipartFile imageFile = new MockMultipartFile(
                "image", "label.jpg", "image/jpeg", "fake-image-data".getBytes());

        mockMvc.perform(multipart("/api/v1/scan").file(imageFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusIa").value("ERRO_IA"))
                .andExpect(jsonPath("$.statusValidacao").value("PENDENTE"));
    }
}
