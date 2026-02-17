package com.visionstock.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionstock.dto.ActionResponseDTO;
import com.visionstock.dto.ProductAdminDTO;
import com.visionstock.dto.ProductCreateDTO;
import com.visionstock.dto.ProductResponseDTO;
import com.visionstock.exception.DuplicateProductException;
import com.visionstock.model.enums.UserRole;
import com.visionstock.model.inventory.ProductImage;
import com.visionstock.security.AuthenticatedUser;
import com.visionstock.security.JwtAuthenticationFilter;
import com.visionstock.service.GeminiService;
import com.visionstock.service.ProductImageService;
import com.visionstock.service.ProductService;
import com.visionstock.service.ValidationService;

@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockBean
        private ProductService productService;

        @MockBean
        private GeminiService geminiService;

        @MockBean
        private ValidationService validationService;

        @MockBean
        private ProductImageService productImageService;

        @MockBean
        private JwtAuthenticationFilter jwtAuthenticationFilter;

        @BeforeEach
        void setAuthenticationContext() {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authenticatedUserToken("USER"));
                SecurityContextHolder.setContext(context);
        }

        @AfterEach
        void clearAuthenticationContext() {
                SecurityContextHolder.clearContext();
        }

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

                when(productService.createProduct(any(ProductCreateDTO.class), any(UUID.class))).thenReturn(responseDTO);

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
                                .precoVenda(new BigDecimal("59.90"))
                                .quantidadeInicial(5)
                                .build();

                when(productService.createProduct(any(ProductCreateDTO.class), any(UUID.class)))
                                .thenThrow(new DuplicateProductException("Product already exists"));

                mockMvc.perform(post("/api/v1/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dto)))
                                .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("GET /api/v1/products should return public fields for USER")
        void listProducts_user_shouldReturnPublicFields() throws Exception {
                UUID productId = UUID.randomUUID();

                ProductResponseDTO responseDTO = ProductResponseDTO.builder()
                                .id(productId)
                                .descricao("Produto de teste")
                                .precoVenda(new BigDecimal("99.90"))
                                .quantidadeAtual(7)
                                .build();

                when(productService.listProductsForUser()).thenReturn(List.of(responseDTO));

                mockMvc.perform(get("/api/v1/products"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].id").value(productId.toString()))
                                .andExpect(jsonPath("$[0].descricao").value("Produto de teste"))
                                .andExpect(jsonPath("$[0].precoVenda").value(99.90))
                                .andExpect(jsonPath("$[0].precoCusto").doesNotExist());
        }

        @Test
        @DisplayName("GET /api/v1/products should return financial fields for ADMIN")
        void listProducts_admin_shouldReturnFinancialFields() throws Exception {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authenticatedUserToken("ADMIN"));
                SecurityContextHolder.setContext(context);

                UUID productId = UUID.randomUUID();
                ProductAdminDTO responseDTO = ProductAdminDTO.builder()
                                .id(productId)
                                .descricao("Produto Admin")
                                .precoCusto(new BigDecimal("45.00"))
                                .precoVenda(new BigDecimal("89.90"))
                                .markupPercentual(new BigDecimal("99.78"))
                                .build();

                when(productService.listProductsForAdmin()).thenReturn(List.of(responseDTO));

                mockMvc.perform(get("/api/v1/products"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].id").value(productId.toString()))
                                .andExpect(jsonPath("$[0].precoCusto").value(45.00))
                                .andExpect(jsonPath("$[0].markupPercentual").value(99.78));
        }

        @Test
        @DisplayName("PATCH /api/v1/products/{productId}/images/{imageId}/primary should return 200 UPDATED for ADMIN")
        void setPrimaryImage_admin_shouldReturn200Updated() throws Exception {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authenticatedUserToken("ADMIN"));
                SecurityContextHolder.setContext(context);

                UUID productId = UUID.randomUUID();
                UUID imageId = UUID.randomUUID();

                ActionResponseDTO response = ActionResponseDTO.builder()
                                .success(true)
                                .status("UPDATED")
                                .message("Imagem principal atualizada")
                                .resourceId(imageId)
                                .build();

                when(productImageService.setPrimaryImage(
                                any(UUID.class),
                                any(UUID.class),
                                any(UserRole.class),
                                any(UUID.class)))
                                .thenReturn(response);

                mockMvc.perform(patch("/api/v1/products/{productId}/images/{imageId}/primary", productId, imageId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.status").value("UPDATED"))
                                .andExpect(jsonPath("$.resourceId").value(imageId.toString()));
        }

        @Test
        @DisplayName("GET /api/v1/products/{productId}/images/{imageId}/content should return bytes with positive content-length")
        void getProductImageContent_shouldReturnBytesWithContentLength() throws Exception {
                UUID productId = UUID.randomUUID();
                UUID imageId = UUID.randomUUID();
                byte[] imageBytes = new byte[] { 1, 2, 3 };

                ProductImage image = ProductImage.builder()
                                .id(imageId)
                                .productId(productId)
                                .fileName("produto.jpg")
                                .contentType("image/jpeg")
                                .fileSize(999L)
                                .imageData(imageBytes)
                                .build();

                when(productImageService.getImageContent(productId, imageId)).thenReturn(image);

                mockMvc.perform(get("/api/v1/products/{productId}/images/{imageId}/content", productId, imageId))
                                .andExpect(status().isOk())
                                .andExpect(header().string("Content-Length", "3"))
                                .andExpect(content().contentType("image/jpeg"))
                                .andExpect(content().bytes(imageBytes));
        }

        private UsernamePasswordAuthenticationToken authenticatedUserToken(String role) {
                AuthenticatedUser principal = new AuthenticatedUser(
                                UUID.randomUUID(),
                                "user@visionstock.com",
                                "$2a$10$dummyhashdummyhashdummyhashdum",
                                role,
                                true);

                return new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        }
}
