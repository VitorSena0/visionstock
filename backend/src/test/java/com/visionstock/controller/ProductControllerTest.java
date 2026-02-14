package com.visionstock.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionstock.dto.ProductCreateDTO;
import com.visionstock.dto.ProductResponseDTO;
import com.visionstock.exception.DuplicateProductException;
import com.visionstock.service.GeminiService;
import com.visionstock.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    @MockBean
    private GeminiService geminiService;

    @Test
    @DisplayName("POST /api/v1/products should return 201 with product data")
    void createProduct_shouldReturn201() throws Exception {
        UUID productId = UUID.randomUUID();

        ProductCreateDTO createDTO = ProductCreateDTO.builder()
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

        ProductResponseDTO responseDTO = ProductResponseDTO.builder()
                .id(productId)
                .referencia("REF-001")
                .descricao("Camiseta Polo Azul")
                .tamanho("M")
                .cor("Azul")
                .marca("Nike")
                .codigoBarras("7891234567890")
                .precoVenda(new BigDecimal("89.90"))
                .quantidadeAtual(10)
                .statusIa("MANUAL")
                .statusValidacao("OK")
                .build();

        when(productService.createProduct(any(ProductCreateDTO.class))).thenReturn(responseDTO);

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(productId.toString()))
                .andExpect(jsonPath("$.descricao").value("Camiseta Polo Azul"))
                .andExpect(jsonPath("$.statusIa").value("MANUAL"))
                .andExpect(jsonPath("$.quantidadeAtual").value(10));
    }

    @Test
    @DisplayName("POST /api/v1/products with missing ID should return 400")
    void createProduct_missingId_shouldReturn400() throws Exception {
        ProductCreateDTO dto = ProductCreateDTO.builder()
                .descricao("Camiseta")
                .quantidadeInicial(5)
                .build();

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/products with missing quantidadeInicial should return 400")
    void createProduct_missingQuantidade_shouldReturn400() throws Exception {
        ProductCreateDTO dto = ProductCreateDTO.builder()
                .id(UUID.randomUUID())
                .descricao("Camiseta")
                .build();

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/products with negative quantidadeInicial should return 400")
    void createProduct_negativeQuantidade_shouldReturn400() throws Exception {
        ProductCreateDTO dto = ProductCreateDTO.builder()
                .id(UUID.randomUUID())
                .descricao("Camiseta")
                .quantidadeInicial(-1)
                .build();

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/products with duplicate ID should return 409")
    void createProduct_duplicateId_shouldReturn409() throws Exception {
        ProductCreateDTO dto = ProductCreateDTO.builder()
                .id(UUID.randomUUID())
                .descricao("Camiseta")
                .quantidadeInicial(5)
                .build();

        when(productService.createProduct(any(ProductCreateDTO.class)))
                .thenThrow(new DuplicateProductException("Product already exists"));

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict());
    }
}
