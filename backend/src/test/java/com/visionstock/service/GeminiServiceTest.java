package com.visionstock.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionstock.dto.ProductResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GeminiServiceTest {

    private GeminiService geminiService;

    @BeforeEach
    void setUp() {
        geminiService = new GeminiService(
                "${gemini.api.key}", "${gemini.api.model}", "${gemini.api.fallback-model}",
                "https://generativelanguage.googleapis.com/v1/models",
                new ObjectMapper());
    }

    @Test
    @DisplayName("parseResponse should extract product data from valid Gemini response")
    void parseResponse_shouldExtractProductData() {
        String geminiResponse = """
                {
                  "candidates": [{
                    "content": {
                      "parts": [{
                        "text": "{\\"descricao\\":\\"Camiseta Polo Azul\\",\\"tamanho\\":\\"M\\",\\"cor\\":\\"Azul\\",\\"marca\\":\\"Nike\\",\\"codigoBarras\\":\\"7891234567890\\",\\"precoVenda\\":89.90}"
                      }]
                    }
                  }]
                }
                """;

        ProductResponseDTO dto = geminiService.parseResponse(geminiResponse);

        assertEquals("Camiseta Polo Azul", dto.getDescricao());
        assertEquals("M", dto.getTamanho());
        assertEquals("Azul", dto.getCor());
        assertEquals("Nike", dto.getMarca());
        assertEquals("7891234567890", dto.getCodigoBarras());
        assertEquals(0, new BigDecimal("89.90").compareTo(dto.getPrecoVenda()));
        assertEquals("IA_SUGERIDO", dto.getStatusIa());
        assertEquals("PENDENTE", dto.getStatusValidacao());
    }

    @Test
    @DisplayName("parseResponse should handle markdown code fences in response")
    void parseResponse_shouldHandleMarkdownCodeFences() {
        String geminiResponse = """
                {
                  "candidates": [{
                    "content": {
                      "parts": [{
                        "text": "```json\\n{\\"descricao\\":\\"Calça Jeans\\",\\"tamanho\\":\\"42\\",\\"cor\\":\\"Azul Escuro\\",\\"marca\\":\\"Levi's\\",\\"codigoBarras\\":null,\\"precoVenda\\":199.90}\\n```"
                      }]
                    }
                  }]
                }
                """;

        ProductResponseDTO dto = geminiService.parseResponse(geminiResponse);

        assertEquals("Calça Jeans", dto.getDescricao());
        assertEquals("42", dto.getTamanho());
        assertEquals("Azul Escuro", dto.getCor());
        assertEquals("Levi's", dto.getMarca());
        assertNull(dto.getCodigoBarras());
        assertEquals(0, new BigDecimal("199.90").compareTo(dto.getPrecoVenda()));
    }

    @Test
    @DisplayName("parseResponse should handle null fields in response")
    void parseResponse_shouldHandleNullFields() {
        String geminiResponse = """
                {
                  "candidates": [{
                    "content": {
                      "parts": [{
                        "text": "{\\"descricao\\":\\"Blusa\\",\\"tamanho\\":null,\\"cor\\":null,\\"marca\\":null,\\"codigoBarras\\":null,\\"precoVenda\\":null}"
                      }]
                    }
                  }]
                }
                """;

        ProductResponseDTO dto = geminiService.parseResponse(geminiResponse);

        assertEquals("Blusa", dto.getDescricao());
        assertNull(dto.getTamanho());
        assertNull(dto.getCor());
        assertNull(dto.getMarca());
        assertNull(dto.getCodigoBarras());
        assertNull(dto.getPrecoVenda());
        assertEquals("IA_SUGERIDO", dto.getStatusIa());
    }

    @Test
    @DisplayName("parseResponse should return error DTO on invalid JSON")
    void parseResponse_shouldReturnErrorOnInvalidJson() {
        String invalidResponse = "this is not valid json";

        ProductResponseDTO dto = geminiService.parseResponse(invalidResponse);

        assertEquals("ERRO_IA", dto.getStatusIa());
        assertEquals("PENDENTE", dto.getStatusValidacao());
    }

    @Test
    @DisplayName("parseResponse should return error DTO on empty candidates")
    void parseResponse_shouldReturnErrorOnEmptyCandidates() {
        String emptyResponse = """
                {
                  "candidates": []
                }
                """;

        ProductResponseDTO dto = geminiService.parseResponse(emptyResponse);

        assertEquals("ERRO_IA", dto.getStatusIa());
    }

    @Test
    @DisplayName("buildRequest should include image data and system instruction")
    void buildRequest_shouldBuildCorrectStructure() {
        Map<String, Object> request = geminiService.buildRequest("base64data", "image/png");

        assertNotNull(request.get("contents"));
        assertNotNull(request.get("systemInstruction"));
    }

    @Test
    @DisplayName("System instruction should instruct AI to return JSON only")
    void systemInstruction_shouldContainJsonRequirement() {
        assertTrue(GeminiService.SYSTEM_INSTRUCTION.contains("APENAS um JSON válido"));
        assertTrue(GeminiService.SYSTEM_INSTRUCTION.contains("descricao"));
        assertTrue(GeminiService.SYSTEM_INSTRUCTION.contains("tamanho"));
        assertTrue(GeminiService.SYSTEM_INSTRUCTION.contains("cor"));
        assertTrue(GeminiService.SYSTEM_INSTRUCTION.contains("marca"));
    }
}
