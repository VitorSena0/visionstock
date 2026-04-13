package com.visionstock.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionstock.dto.ProductResponseDTO;
import com.visionstock.exception.ExternalServiceRateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test for GeminiService using a real image.
 * 
 * To run this test:
 * 1. Place a clothing label image at: src/test/resources/image.jpg
 * 2. Set environment variable GEMINI_API_KEY with a valid API key
 * 3. Run: mvn test -Dtest=GeminiServiceIntegrationTest -Dgroups=integration
 * 
 * Note: This test makes real API calls to Google Gemini and counts against your quota.
 * Tests are tagged as "integration" and skipped by default in normal builds.
 */
@Tag("integration")
class GeminiServiceIntegrationTest {

    private GeminiService geminiService;

    private static final String API_KEY = System.getenv("GEMINI_API_KEY");
    private static final String MODEL = "gemini-2.5-flash";
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    @BeforeEach
    void setUp() {
        // Use assumeTrue to properly skip tests when API key is not available
        assumeTrue(API_KEY != null && !API_KEY.isBlank(), 
                "GEMINI_API_KEY environment variable not set - skipping integration test");
        geminiService = new GeminiService(API_KEY, MODEL, "gemini-2.0-flash", API_URL, new ObjectMapper());
    }

    @Test
    @DisplayName("Should extract product data from real clothing label image")
    void extractDataFromImage_withRealImage_shouldReturnProductData() throws IOException {
        // Load test image from resources
        ClassPathResource imageResource = new ClassPathResource("image.jpg");
        
        assumeTrue(imageResource.exists(), 
                "image.jpg not found in src/test/resources/ - skipping test");

        byte[] imageBytes = Files.readAllBytes(imageResource.getFile().toPath());
        
        MockMultipartFile mockFile = new MockMultipartFile(
                "file",
                "image.jpg",
                "image/jpeg",
                imageBytes
        );

        // Act - Call real Gemini API
        System.out.println("🚀 Calling Gemini API with image...");
        ProductResponseDTO result;
        try {
            result = geminiService.extractDataFromImage(mockFile);
        } catch (ExternalServiceRateLimitException ex) {
            System.out.println("\n⚠️ API rate limited (429)");
            System.out.println("   " + ex.getMessage());
            assumeTrue(false, "API rate limited - skipping assertion");
            return;
        }

        // Assert
        assertNotNull(result, "Result should not be null");
        System.out.println("\n📦 Extracted Product Data:");
        System.out.println("   Descrição: " + result.getDescricao());
        System.out.println("   Tamanho: " + result.getTamanho());
        System.out.println("   Cor: " + result.getCor());
        System.out.println("   Marca: " + result.getMarca());
        System.out.println("   Código de Barras: " + result.getCodigoBarras());
        System.out.println("   Preço Venda: " + result.getPrecoVenda());
        System.out.println("   Status IA: " + result.getStatusIa());
        System.out.println("   Status Validação: " + result.getStatusValidacao());

        // Verify successful extraction
        assertEquals("IA_SUGERIDO", result.getStatusIa(), 
                "Status should be IA_SUGERIDO for successful extraction");
        assertEquals("PENDENTE", result.getStatusValidacao(), 
                "Validation status should be PENDENTE");
    }

    @Test
    @DisplayName("Should handle PNG image format")
    void extractDataFromImage_withPngImage_shouldWork() throws IOException {
        ClassPathResource imageResource = new ClassPathResource("image.png");
        
        assumeTrue(imageResource.exists(), 
                "image.png not found - skipping PNG test");

        byte[] imageBytes = Files.readAllBytes(imageResource.getFile().toPath());
        
        MockMultipartFile mockFile = new MockMultipartFile(
                "file",
                "image.png",
                "image/png",
                imageBytes
        );

        ProductResponseDTO result;
        try {
            result = geminiService.extractDataFromImage(mockFile);
        } catch (ExternalServiceRateLimitException ex) {
            assumeTrue(false, "API rate limited - skipping");
            return;
        }

        assertNotNull(result);
        
        assertEquals("IA_SUGERIDO", result.getStatusIa());
    }
}
