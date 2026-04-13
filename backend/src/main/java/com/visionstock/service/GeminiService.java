package com.visionstock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionstock.dto.ProductResponseDTO;
import com.visionstock.exception.ExternalServiceException;
import com.visionstock.exception.ExternalServiceRateLimitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class GeminiService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);
    private static final int MAX_GEMINI_ATTEMPTS = 3;
    private static final long INITIAL_RETRY_BACKOFF_MS = 1200L;
    private static final long MAX_IMAGE_BYTES_FOR_DIRECT_SEND = 1_500_000L;
    private static final int MAX_IMAGE_SIDE_PX = 1800;
    private static final float JPEG_QUALITY = 0.82f;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String fallbackModel;

    static final String SYSTEM_INSTRUCTION = """
            Você é um especialista em vestuário e moda. Sua função é analisar imagens de etiquetas \
            de roupas e extrair informações do produto.

            Analise a imagem fornecida e extraia as seguintes informações:
            - referencia: número de referência da peça (ex: REF 12345, Referência ABC-10)
            - descricao: descrição do produto 
            - tamanho: tamanho indicado na etiqueta 
            - cor: cor do produto 
            - marca: marca do produto 
            - codigoBarras: código de barras se visível 
            - precoVenda: preço de venda se visível, apenas o número 

            REGRAS IMPORTANTES:
            1. Retorne APENAS um JSON válido, sem markdown, sem crases, sem explicações.
            2. Se não conseguir identificar um campo, use null.
            3. O campo precoVenda deve ser um número decimal ou null.
            4. Para referencia, quando houver prefixos como REF, REF., REFERENCIA, RETORNE apenas o valor da referência.
            5. Use o seguinte formato exato:

            {"referencia":"...","descricao":"...","tamanho":"...","cor":"...","marca":"...","codigoBarras":"...","precoVenda":null}
            """;

    public GeminiService(
            @Value("${gemini.api.key}") String apiKey,
            @Value("${gemini.api.model}") String model,
            @Value("${gemini.api.fallback-model:}") String fallbackModel,
            @Value("${gemini.api.url}") String apiUrl,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.fallbackModel = fallbackModel;
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
            if (apiKey == null || apiKey.isBlank()) {
                logger.error("Gemini API key is not configured. Set GEMINI_API_KEY before scanning.");
                throw new ExternalServiceException(
                        "Integracao da IA nao configurada no servidor (GEMINI_API_KEY ausente).");
            }

            EncodedImage encodedImage = prepareImagePayload(file);
            String base64Image = encodedImage.base64();
            String mimeType = encodedImage.mimeType();
            logger.info(
                    "Sending image to Gemini: file={}, mimeType={}, size={} bytes, optimized={}",
                    file.getOriginalFilename(),
                    mimeType,
                    encodedImage.byteSize(),
                    encodedImage.optimized());

            Map<String, Object> requestBody = buildRequest(base64Image, mimeType);
            String response = callGeminiWithRetry(requestBody, startTime, model, true);

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("Gemini API response received in {} ms", elapsed);

            return parseResponse(response);
        } catch (ExternalServiceRateLimitException ex) {
            long elapsed = System.currentTimeMillis() - startTime;
            logger.error("Gemini API rate limit after {} ms: {}", elapsed, ex.getMessage());
            throw ex;
        } catch (ExternalServiceException ex) {
            long elapsed = System.currentTimeMillis() - startTime;
            logger.error("Gemini API unavailable after {} ms: {}", elapsed, ex.getMessage());
            throw ex;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            logger.error("Gemini API call failed after {} ms", elapsed, e);
            throw new ExternalServiceException(
                    "Falha ao consultar a IA no momento. Tente novamente em instantes.");
        }
    }

    private String callGeminiWithRetry(Map<String, Object> requestBody,
                                       long requestStartTime,
                                       String modelToUse,
                                       boolean canUseFallback) {
        for (int attempt = 1; attempt <= MAX_GEMINI_ATTEMPTS; attempt++) {
            try {
                return webClient.post()
                        .uri("/{model}:generateContent?key={key}", modelToUse, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
            } catch (WebClientResponseException.TooManyRequests ex) {
                Integer retryAfterSeconds = parseRetryAfterSeconds(ex.getHeaders());
                boolean shouldRetry = attempt < MAX_GEMINI_ATTEMPTS;
                if (shouldRetry) {
                    long backoffMs = computeBackoffMs(attempt, retryAfterSeconds);
                    logger.warn(
                            "Gemini returned 429 (attempt {}/{}). Retrying in {} ms",
                            attempt,
                            MAX_GEMINI_ATTEMPTS,
                            backoffMs);
                    sleep(backoffMs);
                    continue;
                }

                long elapsed = System.currentTimeMillis() - requestStartTime;
                logger.error("Gemini API call failed after {} ms: {}", elapsed, ex.getMessage());
                throw new ExternalServiceRateLimitException(
                        "Limite temporario da IA atingido. Aguarde alguns segundos e tente novamente.",
                        retryAfterSeconds);
            } catch (WebClientResponseException ex) {
                boolean retryable = ex.getStatusCode().is5xxServerError();
                boolean shouldRetry = retryable && attempt < MAX_GEMINI_ATTEMPTS;
                if (shouldRetry) {
                    long backoffMs = computeBackoffMs(attempt, null);
                    logger.warn(
                            "Gemini returned {} (attempt {}/{}). Retrying in {} ms",
                            ex.getStatusCode().value(),
                            attempt,
                            MAX_GEMINI_ATTEMPTS,
                            backoffMs);
                    sleep(backoffMs);
                    continue;
                }

                logger.error(
                        "Gemini returned HTTP {} with body snippet: {}",
                        ex.getStatusCode().value(),
                        truncate(ex.getResponseBodyAsString(), 400));

                if (ex.getStatusCode().is5xxServerError()
                        && canUseFallback
                        && hasFallbackModelConfigured(modelToUse)) {
                    logger.warn(
                            "Primary model {} unavailable after retries. Switching to fallback model {}",
                            modelToUse,
                            fallbackModel);
                    return callGeminiWithRetry(requestBody, requestStartTime, fallbackModel, false);
                }

                throw new ExternalServiceException(
                        "Falha ao consultar a IA (HTTP " + ex.getStatusCode().value() + ")");
            }
        }

        throw new ExternalServiceException("Falha ao consultar a IA no momento. Tente novamente.");
    }

    private EncodedImage prepareImagePayload(MultipartFile file) throws Exception {
        byte[] original = file.getBytes();
        String originalMimeType = file.getContentType() != null ? file.getContentType() : "image/jpeg";

        if (original.length <= MAX_IMAGE_BYTES_FOR_DIRECT_SEND) {
            return new EncodedImage(Base64.getEncoder().encodeToString(original), originalMimeType, original.length, false);
        }

        BufferedImage buffered = ImageIO.read(file.getInputStream());
        if (buffered == null) {
            logger.warn("Could not decode image for optimization. Sending original bytes.");
            return new EncodedImage(Base64.getEncoder().encodeToString(original), originalMimeType, original.length, false);
        }

        BufferedImage resized = resizeIfNeeded(buffered);
        byte[] compressedJpeg = toJpeg(resized);
        if (compressedJpeg == null || compressedJpeg.length == 0) {
            logger.warn("Image optimization produced empty result. Sending original bytes.");
            return new EncodedImage(Base64.getEncoder().encodeToString(original), originalMimeType, original.length, false);
        }

        if (compressedJpeg.length >= original.length) {
            logger.info(
                    "Image optimization not beneficial (optimized={} bytes, original={} bytes). Using original.",
                    compressedJpeg.length,
                    original.length);
            return new EncodedImage(Base64.getEncoder().encodeToString(original), originalMimeType, original.length, false);
        }

        logger.info(
                "Image optimized for Gemini request: original={} bytes, optimized={} bytes",
                original.length,
                compressedJpeg.length);

        return new EncodedImage(
                Base64.getEncoder().encodeToString(compressedJpeg),
                "image/jpeg",
                compressedJpeg.length,
                true);
    }

    private BufferedImage resizeIfNeeded(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int maxSide = Math.max(width, height);

        if (maxSide <= MAX_IMAGE_SIDE_PX) {
            return source;
        }

        double scale = (double) MAX_IMAGE_SIDE_PX / maxSide;
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private byte[] toJpeg(BufferedImage image) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            return null;
        }

        ImageWriter writer = writers.next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }

        return output.toByteArray();
    }

    private boolean hasFallbackModelConfigured(String currentModel) {
        return fallbackModel != null
                && !fallbackModel.isBlank()
                && !fallbackModel.equalsIgnoreCase(currentModel);
    }

    private long computeBackoffMs(int attempt, Integer retryAfterSeconds) {
        if (retryAfterSeconds != null && retryAfterSeconds > 0) {
            long withBuffer = Math.min((retryAfterSeconds * 1000L) + 250L, 8000L);
            return Math.max(withBuffer, INITIAL_RETRY_BACKOFF_MS);
        }

        long exponential = INITIAL_RETRY_BACKOFF_MS * (1L << Math.max(0, attempt - 1));
        return Math.min(exponential, 5000L);
    }

    private Integer parseRetryAfterSeconds(HttpHeaders headers) {
        String raw = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
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
                "systemInstruction", systemInstruction,
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "temperature", 0.2
                )
        );
    }

    ProductResponseDTO parseResponse(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            String finishReason = root.path("candidates").path(0).path("finishReason").asText("UNKNOWN");
            String text = root.path("candidates").path(0)
                    .path("content").path("parts").path(0)
                    .path("text").asText();

            if (text == null || text.isBlank()) {
                logger.error(
                        "Gemini response contained no text content. finishReason={}, responseSnippet={}",
                        finishReason,
                        truncate(responseJson, 400));
                return buildErrorDTO();
            }

            // Clean up markdown code fences if present
            text = text.replaceAll("```(?:json)?\\s*", "").trim();

            JsonNode data = objectMapper.readTree(text);
            if (data == null || data.isMissingNode()) {
                logger.error("Failed to parse Gemini response text as JSON");
                return buildErrorDTO();
            }

                String referencia = normalizeReference(getFirstTextOrNull(
                    data,
                    "referencia",
                    "referência",
                    "ref",
                    "numeroReferencia",
                    "numero_referencia",
                    "codigoReferencia",
                    "codigo_ref"));
                String descricao = getTextOrNull(data, "descricao");
            String tamanho = getTextOrNull(data, "tamanho");
            String cor = getTextOrNull(data, "cor");
            String marca = getTextOrNull(data, "marca");
            String codigoBarras = getTextOrNull(data, "codigoBarras");
            BigDecimal precoVenda = getDecimalOrNull(data, "precoVenda");

                if (isAllMainFieldsEmpty(referencia, descricao, tamanho, cor, marca, codigoBarras, precoVenda)) {
                logger.warn(
                        "Gemini extracted empty result (all fields null). finishReason={}, modelPayloadSnippet={}",
                        finishReason,
                        truncate(text, 300));
            }

            return ProductResponseDTO.builder()
                    .referencia(referencia)
                    .descricao(descricao)
                    .tamanho(tamanho)
                    .cor(cor)
                    .marca(marca)
                    .codigoBarras(codigoBarras)
                    .precoVenda(precoVenda)
                    .statusIa("IA_SUGERIDO")
                    .statusValidacao("PENDENTE")
                    .build();
        } catch (Exception e) {
            logger.error("Failed to parse Gemini response", e);
            return buildErrorDTO();
        }
    }

    private boolean isAllMainFieldsEmpty(String referencia,
                                         String descricao,
                                         String tamanho,
                                         String cor,
                                         String marca,
                                         String codigoBarras,
                                         BigDecimal precoVenda) {
        return Stream.of(referencia, descricao, tamanho, cor, marca, codigoBarras)
                .allMatch(value -> value == null || value.isBlank())
                && precoVenda == null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "null";
        }

        String sanitized = value.replaceAll("\\s+", " ").trim();
        if (sanitized.length() <= maxLength) {
            return sanitized;
        }

        return sanitized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String getTextOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private String getFirstTextOrNull(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = getTextOrNull(node, field);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String normalizeReference(String referenciaRaw) {
        if (referenciaRaw == null || referenciaRaw.isBlank()) {
            return null;
        }

        String trimmed = referenciaRaw.trim();
        String withoutPrefix = trimmed.replaceFirst("(?i)^\\s*(ref(?:er[êe]ncia)?\\.?|refer[êe]ncia\\.?|c[óo]d(?:igo)?\\.?\\s*ref\\.?)\\s*[:#-]?\\s*", "");
        String normalized = withoutPrefix.trim();
        return normalized.isBlank() ? null : normalized;
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

    private record EncodedImage(String base64, String mimeType, int byteSize, boolean optimized) {
    }
}
