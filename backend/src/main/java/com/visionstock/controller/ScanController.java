package com.visionstock.controller;

import com.visionstock.dto.ProductResponseDTO;
import com.visionstock.service.GeminiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller for AI-powered product label scanning.
 * This endpoint does NOT save to the database — it returns a draft for user review
 * following the "Review-Before-Commit" pattern.
 */
@RestController
@RequestMapping("/api/v1/scan")
public class ScanController {

    private static final Logger logger = LoggerFactory.getLogger(ScanController.class);

    private final GeminiService geminiService;

    public ScanController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponseDTO> scanImage(
            @RequestParam("image") MultipartFile image) {

        logger.info("Scan request received: file={}, size={} bytes",
                image.getOriginalFilename(), image.getSize());

        if (image.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        ProductResponseDTO result = geminiService.extractDataFromImage(image);
        return ResponseEntity.ok(result);
    }
}
