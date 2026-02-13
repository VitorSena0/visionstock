package com.visionstock.service;

import com.visionstock.dto.ProductCreateDTO;
import com.visionstock.dto.ProductResponseDTO;
import com.visionstock.exception.DuplicateProductException;
import com.visionstock.model.enums.MovementType;
import com.visionstock.model.finance.StockMovement;
import com.visionstock.model.inventory.Product;
import com.visionstock.repository.ProductRepository;
import com.visionstock.repository.StockMovementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class ProductService {

    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);
    private static final UUID SYSTEM_USER_ID = new UUID(0, 0);

    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;

    public ProductService(ProductRepository productRepository,
                          StockMovementRepository stockMovementRepository) {
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
    }

    @Transactional
    public ProductResponseDTO createProduct(ProductCreateDTO dto) {
        logger.info("Creating product with ID: {}", dto.getId());

        // 1. Check for duplicate by ID
        if (productRepository.existsById(dto.getId())) {
            throw new DuplicateProductException(
                    "Product with ID " + dto.getId() + " already exists");
        }

        // 2. Check for duplicate by barcode (if provided)
        if (dto.getCodigoBarras() != null && !dto.getCodigoBarras().isBlank()) {
            if (productRepository.existsByCodigoBarras(dto.getCodigoBarras())) {
                throw new DuplicateProductException(
                        "Product with barcode " + dto.getCodigoBarras() + " already exists");
            }
        }

        // 3. Build and save Product entity
        Product product = Product.builder()
                .id(dto.getId())
                .referencia(dto.getReferencia())
                .descricao(dto.getDescricao())
                .tamanho(dto.getTamanho())
                .cor(dto.getCor())
                .marca(dto.getMarca())
                .codigoBarras(dto.getCodigoBarras())
                .precoCusto(dto.getPrecoCusto())
                .precoVenda(dto.getPrecoVenda())
                .quantidadeAtual(dto.getQuantidadeInicial() != null ? dto.getQuantidadeInicial() : 0)
                .statusIa("VERIFICADO")
                .statusValidacao("OK")
                .build();

        product = productRepository.save(product);

        // 4. Create initial stock movement if quantidadeInicial > 0
        if (dto.getQuantidadeInicial() != null && dto.getQuantidadeInicial() > 0) {
            StockMovement movement = StockMovement.builder()
                    .id(UUID.randomUUID())
                    .productId(product.getId())
                    .userId(product.getCreatedBy() != null ? product.getCreatedBy() : SYSTEM_USER_ID)
                    .tipoMovimento(MovementType.ENTRADA.name())
                    .quantidade(dto.getQuantidadeInicial())
                    .valorUnitario(dto.getPrecoCusto() != null ? dto.getPrecoCusto() : BigDecimal.ZERO)
                    .observacao("Entrada inicial - cadastro de produto")
                    .build();

            stockMovementRepository.save(movement);
            logger.info("Initial stock movement created for product {}: {} units",
                    product.getId(), dto.getQuantidadeInicial());
        }

        logger.info("Product created successfully: {}", product.getId());
        return ProductResponseDTO.fromEntity(product);
    }
}
