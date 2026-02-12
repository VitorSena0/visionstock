package com.visionstock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionstock.dto.ProductResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    static final String SYSTEM_INSTRUCTION = """
            Você é um especialista em vestuário e moda. Sua função é analisar imagens de etiquetas \
            de roupas e extrair informações do produto.

            Analise a imagem fornecida e extraia as seguintes informações:
            - descricao: descrição do produto (ex: "Camiseta Polo Masculina")
            - tamanho: tamanho indicado na etiqueta (ex: "M", "G", "42")
            - cor: cor do produto (ex: "Azul Marinho")
            - marca: marca do produto (ex: "Nike")
            - codigoBarras: código de barras se visível (ex: "7891234567890")
            - precoVenda: preço de venda se visível, apenas o número (ex: 89.90)

            REGRAS IMPORTANTES:
            1. Retorne APENAS um JSON válido, sem markdown, sem crases, sem explicações.
            2. Se não conseguir identificar um campo, use null.
            3. O campo precoVenda deve ser um número decimal ou null.
            4. Use o seguinte formato exato:

            {"descricao":"...","tamanho":"...","cor":"...","marca":"...","codigoBarras":"...","precoVenda":null}
            """;

    public GeminiService(
            @Value("${gemini.api.key}") String apiKey,
            @Value("${gemini.api.model}") String model,
            @Value("${gemini.api.url}") String apiUrl,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(apiUrl)
                .build();
    }

    /**
     * Extracts product data from a clothing label image using Google Gemini AI.
     * This method does NOT save to the database — it returns a draft DTO for user review.
     */
    public ProductResponseDTO extractDataFromImage(MultipartFile file) {
        long startTime = System.currentTimeMillis();
        try {
            String base64Image = Base64.getEncoder().encodeToString(file.getBytes());
            String mimeType = file.getContentType() != null ? file.getContentType() : "image/jpeg";

            Map<String, Object> requestBody = buildRequest(base64Image, mimeType);

            String response = webClient.post()
                    .uri("/{model}:generateContent?key={key}", model, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("Gemini API response received in {} ms", elapsed);

            return parseResponse(response);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            logger.error("Gemini API call failed after {} ms: {}", elapsed, e.getMessage());
            return buildErrorDTO();
        }
    }

    Map<String, Object> buildRequest(String base64Image, String mimeType) {
        Map<String, Object> inlineData = Map.of(
                "mimeType", mimeType,
                "data", base64Image
        );

        Map<String, Object> imagePart = Map.of("inlineData", inlineData);
        Map<String, Object> textPart = Map.of("text", "Analise esta etiqueta de roupa e extraia os dados do produto.");

        Map<String, Object> content = Map.of(
                "parts", List.of(imagePart, textPart)
        );

        Map<String, Object> systemPart = Map.of("text", SYSTEM_INSTRUCTION);
        Map<String, Object> systemInstruction = Map.of("parts", List.of(systemPart));

        return Map.of(
                "contents", List.of(content),
                "systemInstruction", systemInstruction
        );
    }

    ProductResponseDTO parseResponse(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            String text = root.path("candidates").path(0)
                    .path("content").path("parts").path(0)
                    .path("text").asText();

            if (text == null || text.isBlank()) {
                logger.error("Gemini response contained no text content");
                return buildErrorDTO();
            }

            // Clean up markdown code fences if present
            text = text.replaceAll("```json\\s*", "")
                       .replaceAll("```\\s*", "")
                       .trim();

            JsonNode data = objectMapper.readTree(text);
            if (data == null || data.isMissingNode()) {
                logger.error("Failed to parse Gemini response text as JSON");
                return buildErrorDTO();
            }

            return ProductResponseDTO.builder()
                    .descricao(getTextOrNull(data, "descricao"))
                    .tamanho(getTextOrNull(data, "tamanho"))
                    .cor(getTextOrNull(data, "cor"))
                    .marca(getTextOrNull(data, "marca"))
                    .codigoBarras(getTextOrNull(data, "codigoBarras"))
                    .precoVenda(getDecimalOrNull(data, "precoVenda"))
                    .statusIa("IA_SUGERIDO")
                    .statusValidacao("PENDENTE")
                    .build();
        } catch (Exception e) {
            logger.error("Failed to parse Gemini response: {}", e.getMessage());
            return buildErrorDTO();
        }
    }

    private String getTextOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private BigDecimal getDecimalOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ProductResponseDTO buildErrorDTO() {
        return ProductResponseDTO.builder()
                .statusIa("ERRO_IA")
                .statusValidacao("PENDENTE")
                .build();
    }
}
