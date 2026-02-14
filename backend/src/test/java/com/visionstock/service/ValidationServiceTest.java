package com.visionstock.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionstock.dto.ProductUpdateDTO;
import com.visionstock.dto.ValidationRequestDTO;
import com.visionstock.exception.ResourceNotFoundException;
import com.visionstock.model.enums.ValidationStatus;
import com.visionstock.model.inventory.Product;
import com.visionstock.model.inventory.ValidationRequest;
import com.visionstock.repository.ProductRepository;
import com.visionstock.repository.ValidationRequestRepository;

@ExtendWith(MockitoExtension.class)
class ValidationServiceTest {

    @Mock
    private ValidationRequestRepository validationRequestRepository;

    @Mock
    private ProductRepository productRepository;

    private ObjectMapper objectMapper;

    @InjectMocks
    private ValidationService validationService;

    private Product testProduct;
    private ValidationRequest testRequest;
    private UUID productId;
    private UUID userId;
    private UUID adminId;
    private UUID requestId;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        userId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        requestId = UUID.randomUUID();

        // Use a real ObjectMapper instead of a mock
        objectMapper = new ObjectMapper();
        validationService = new ValidationService(
                validationRequestRepository,
                productRepository,
                objectMapper);

        testProduct = Product.builder()
                .id(productId)
                .referencia("REF-001")
                .descricao("Camiseta Azul")
                .cor("Azul")
                .tamanho("M")
                .marca("Nike")
                .codigoBarras("7891234567890")
                .precoCusto(new BigDecimal("45.00"))
                .precoVenda(new BigDecimal("89.90"))
                .quantidadeAtual(50)
                .quantidadeMinima(10)
                .statusIa("MANUAL")
                .statusValidacao("OK")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        testRequest = ValidationRequest.builder()
                .id(requestId)
                .product(testProduct)
                .productId(productId)
                .requestedBy(userId)
                .status(ValidationStatus.PENDING)
                .originalData("{\"id\":\"" + productId
                        + "\",\"descricao\":\"Camiseta Azul\",\"cor\":\"Azul\",\"tamanho\":\"M\",\"precoVenda\":\"89.90\"}")
                .newData("{\"id\":\"" + productId
                        + "\",\"descricao\":\"Camiseta Verde\",\"cor\":\"Verde\",\"tamanho\":\"M\",\"precoVenda\":\"89.90\"}")
                .requestedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("createValidationRequest should create and save a validation request")
    void createValidationRequest_shouldCreateAndSave() throws Exception {
        ProductUpdateDTO updateDTO = ProductUpdateDTO.builder()
                .descricao("Camiseta Verde")
                .cor("Verde")
                .build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
        when(validationRequestRepository.save(any(ValidationRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ValidationRequest result = validationService.createValidationRequest(productId, updateDTO, userId);

        assertNotNull(result);
        assertEquals(productId, result.getProductId());
        assertEquals(userId, result.getRequestedBy());
        assertEquals(ValidationStatus.PENDING, result.getStatus());
        verify(validationRequestRepository).save(any(ValidationRequest.class));
    }

    @Test
    @DisplayName("createValidationRequest should throw exception when product not found")
    void createValidationRequest_productNotFound_shouldThrowException() {
        ProductUpdateDTO updateDTO = ProductUpdateDTO.builder()
                .descricao("Camiseta Verde")
                .build();

        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> validationService.createValidationRequest(productId, updateDTO, userId));

        verify(validationRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("getPendingRequests should return list of pending requests")
    void getPendingRequests_shouldReturnList() throws Exception {
        List<ValidationRequest> pendingRequests = Arrays.asList(testRequest);

        when(validationRequestRepository.findByStatusOrderByRequestedAtAsc(ValidationStatus.PENDING))
                .thenReturn(pendingRequests);

        List<ValidationRequestDTO> result = validationService.getPendingRequests();

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(validationRequestRepository).findByStatusOrderByRequestedAtAsc(ValidationStatus.PENDING);
    }

    @Test
    @DisplayName("getRequestsByProduct should return requests for specific product")
    void getRequestsByProduct_shouldReturnProductRequests() throws Exception {
        List<ValidationRequest> requests = Arrays.asList(testRequest);

        when(validationRequestRepository.findByProductIdOrderByRequestedAtDesc(productId))
                .thenReturn(requests);

        List<ValidationRequestDTO> result = validationService.getRequestsByProduct(productId);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(validationRequestRepository).findByProductIdOrderByRequestedAtDesc(productId);
    }

    @Test
    @DisplayName("approveRequest should update product and mark request as approved")
    void approveRequest_shouldApproveAndUpdateProduct() throws Exception {
        String reviewNote = "Alteração aprovada";

        when(validationRequestRepository.findById(requestId)).thenReturn(Optional.of(testRequest));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(validationRequestRepository.save(any(ValidationRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = validationService.approveRequest(requestId, adminId, reviewNote);

        assertNotNull(result);
        verify(productRepository).save(any(Product.class));
        verify(validationRequestRepository).save(any(ValidationRequest.class));
    }

    @Test
    @DisplayName("approveRequest should throw exception when request not found")
    void approveRequest_requestNotFound_shouldThrowException() {
        when(validationRequestRepository.findById(requestId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> validationService.approveRequest(requestId, adminId, "Note"));

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("approveRequest should throw exception when request is not pending")
    void approveRequest_notPending_shouldThrowException() {
        testRequest.setStatus(ValidationStatus.APPROVED);
        when(validationRequestRepository.findById(requestId)).thenReturn(Optional.of(testRequest));

        assertThrows(IllegalStateException.class,
                () -> validationService.approveRequest(requestId, adminId, "Note"));

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejectRequest should mark request as rejected without changing product")
    void rejectRequest_shouldRejectWithoutUpdatingProduct() {
        String reviewNote = "Alteração não aprovada";

        when(validationRequestRepository.findById(requestId)).thenReturn(Optional.of(testRequest));
        when(validationRequestRepository.save(any(ValidationRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = validationService.rejectRequest(requestId, adminId, reviewNote);

        assertNotNull(result);
        assertEquals(testProduct, result);
        verify(validationRequestRepository).save(any(ValidationRequest.class));
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejectRequest should throw exception when request not found")
    void rejectRequest_requestNotFound_shouldThrowException() {
        when(validationRequestRepository.findById(requestId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> validationService.rejectRequest(requestId, adminId, "Note"));
    }

    @Test
    @DisplayName("rejectRequest should throw exception when request is not pending")
    void rejectRequest_notPending_shouldThrowException() {
        testRequest.setStatus(ValidationStatus.REJECTED);
        when(validationRequestRepository.findById(requestId)).thenReturn(Optional.of(testRequest));

        assertThrows(IllegalStateException.class,
                () -> validationService.rejectRequest(requestId, adminId, "Note"));
    }

    @Test
    @DisplayName("getPendingRequestCount should return count of pending requests")
    void getPendingRequestCount_shouldReturnCount() {
        when(validationRequestRepository.countByStatus(ValidationStatus.PENDING)).thenReturn(5L);

        Long count = validationService.getPendingRequestCount();

        assertEquals(5L, count);
        verify(validationRequestRepository).countByStatus(ValidationStatus.PENDING);
    }
}
