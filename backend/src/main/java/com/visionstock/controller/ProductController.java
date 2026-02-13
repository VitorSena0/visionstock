package com.visionstock.controller;

import com.visionstock.dto.ProductCreateDTO;
import com.visionstock.dto.ProductResponseDTO;
import com.visionstock.service.ProductService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ProductResponseDTO> createProduct(
            @Valid @RequestBody ProductCreateDTO dto) {

        logger.info("POST /api/v1/products - Creating product with ID: {}", dto.getId());
        ProductResponseDTO response = productService.createProduct(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
