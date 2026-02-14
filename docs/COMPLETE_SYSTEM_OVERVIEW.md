# VisionStock - Visão Completa do Sistema

**Versão:** 1.1.0  
**Data:** 14 de fevereiro de 2026  
**Tipo:** Documentação Técnica Completa

---

## 📋 Índice

1. [Visão Geral](#-visão-geral)
2. [Arquitetura do Sistema](#-arquitetura-do-sistema)
3. [Banco de Dados](#-banco-de-dados)
4. [Backend - Spring Boot](#-backend---spring-boot)
5. [APIs REST](#-apis-rest)
6. [Integração com IA](#-integração-com-ia)
7. [Fluxo de Approval Workflow](#-fluxo-de-approval-workflow)
8. [Funcionalidades Principais](#-funcionalidades-principais)
9. [Tecnologias Utilizadas](#-tecnologias-utilizadas)
10. [Diagramas](#-diagramas)

---

## 🎯 Visão Geral

**VisionStock** é um sistema completo de gerenciamento de estoque de vestuário que utiliza **Inteligência Artificial** para automatizar o cadastro de produtos através da análise de fotos de etiquetas.

### Características-Chave

| Característica | Descrição |
|----------------|-----------|
| 🤖 **IA Integrada** | Google Gemini 2.5 Flash para OCR e extração de dados |
| 📴 **Offline-First** | Funciona completamente sem internet |
| 🔐 **Controle de Acesso** | Sistema de roles (ADMIN/USER) com workflow de aprovação |
| 💰 **Gestão Financeira** | Controle completo de custos, vendas e margens |
| 📊 **Relatórios** | Dashboards de lucratividade e análise de estoque |
| 🔄 **Sincronização** | Sistema de sync para ambientes offline-first |
| 🛡️ **Auditoria** | Log completo de todas as operações |

### Público-Alvo

- **Pequenas e médias lojas de vestuário**
- **Estoquistas** que precisam controlar inventário rapidamente
- **Gerentes** que precisam aprovar mudanças e visualizar relatórios

---

## 🏗️ Arquitetura do Sistema

### Visão Macro

```
┌─────────────────────────────────────────────────────────────┐
│                    FRONTEND (Flutter)                        │
│  • Offline-First com SQLite local                           │
│  • Scanner de códigos de barras                             │
│  • Câmera para fotos de etiquetas                           │
│  • Interface mobile-first                                    │
└────────────────────┬────────────────────────────────────────┘
                     │ REST APIs (JSON)
                     │ Sync quando online
                     ▼
┌─────────────────────────────────────────────────────────────┐
│               BACKEND (Spring Boot 3.2.5)                    │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ Controllers (REST)                                    │  │
│  │  • ProductController                                  │  │
│  │  • ScanController                                     │  │
│  └────────────┬─────────────────────────────────────────┘  │
│               │                                              │
│  ┌────────────▼─────────────────────────────────────────┐  │
│  │ Services (Business Logic)                            │  │
│  │  • ProductService         • ValidationService        │  │
│  │  • GeminiService          • (Future: AuthService)    │  │
│  └────────────┬─────────────────────────────────────────┘  │
│               │                                              │
│  ┌────────────▼─────────────────────────────────────────┐  │
│  │ Repositories (Data Access - Spring Data JPA)         │  │
│  │  • ProductRepository                                  │  │
│  │  • ValidationRequestRepository                        │  │
│  │  • StockMovementRepository                            │  │
│  └────────────┬─────────────────────────────────────────┘  │
└───────────────┼──────────────────────────────────────────────┘
                │ JPA/Hibernate
                ▼
┌─────────────────────────────────────────────────────────────┐
│            DATABASE (PostgreSQL 15+)                         │
│  Multi-Schema Architecture:                                  │
│  • auth       → Usuários e autenticação                     │
│  • inventory  → Produtos e validações                        │
│  • finance    → Movimentações financeiras                    │
│  • system     → Auditoria e logs                            │
└─────────────────────────────────────────────────────────────┘

                     │
                     │ API REST
                     ▼
┌─────────────────────────────────────────────────────────────┐
│          SERVIÇO EXTERNO (Google Gemini AI)                  │
│  • Extração de dados de imagens                             │
│  • OCR de etiquetas de vestuário                            │
│  • Análise de texto estruturado                             │
└─────────────────────────────────────────────────────────────┘
```

### Padrões Arquiteturais

- **Layered Architecture** (Camadas: Controller → Service → Repository)
- **Strategy Pattern** (updateProduct com lógica baseada em role)
- **DTO Pattern** (separação entre entidades e DTOs de transporte)
- **Repository Pattern** (abstração de acesso a dados)
- **Offline-First Pattern** (dados locais primeiro, sync depois)

---

## 🗄️ Banco de Dados

### Arquitetura Multi-Schema

O VisionStock utiliza PostgreSQL com **4 schemas separados** para organização modular:

```sql
visionstock (database)
  ├── auth         -- Autenticação e usuários
  ├── inventory    -- Produtos e estoque
  ├── finance      -- Movimentações financeiras
  └── system       -- Auditoria e logs
```

### Schema: `auth` - Autenticação e Usuários

#### Tabela: `auth.roles`
```sql
CREATE TABLE auth.roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL UNIQUE,           -- ADMIN, USER
    description TEXT,
    permissions JSONB DEFAULT '{}',              -- Permissões em JSON
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    deleted_at TIMESTAMP WITH TIME ZONE
);
```

**Propósito:** Define os papéis no sistema (ADMIN = Gerente, USER = Estoquista)

#### Tabela: `auth.users`
```sql
CREATE TABLE auth.users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    role_id UUID REFERENCES auth.roles(id),
    ativo BOOLEAN DEFAULT true,
    ultimo_acesso TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_role CHECK (role IN ('ADMIN', 'USER'))
);
```

**Campos Importantes:**
- `role`: ADMIN (pode aprovar mudanças) ou USER (precisa de aprovação)
- `ativo`: Controla se o usuário pode acessar o sistema
- `deleted_at`: Soft delete - nunca deleta fisicamente

---

### Schema: `inventory` - Produtos e Estoque

#### Tabela: `inventory.categories`
```sql
CREATE TABLE inventory.categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome VARCHAR(100) NOT NULL UNIQUE,
    descricao TEXT,
    parent_id UUID REFERENCES inventory.categories(id),  -- Hierarquia
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    deleted_at TIMESTAMP WITH TIME ZONE
);
```

**Propósito:** Categorias hierárquicas (Roupas → Camisetas → Polo)

#### Tabela: `inventory.products` ⭐ **TABELA PRINCIPAL**
```sql
CREATE TABLE inventory.products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Identificação
    referencia VARCHAR(100) UNIQUE,              -- Código interno
    codigo_barras VARCHAR(100) UNIQUE,           -- EAN/UPC
    imagem_url TEXT,                             -- URL da foto
    
    -- Detalhes do Produto
    descricao TEXT NOT NULL,                     -- Ex: "Camiseta Polo Azul"
    tamanho VARCHAR(10),                         -- P, M, G, GG
    cor VARCHAR(50),                             -- Azul, Vermelho
    marca VARCHAR(100),                          -- Nike, Adidas
    category_id UUID REFERENCES inventory.categories(id),
    
    -- Financeiro
    preco_custo DECIMAL(10,2),                   -- Custo de compra
    preco_venda DECIMAL(10,2) NOT NULL,          -- Preço de venda
    markup_percentual DECIMAL(5,2) GENERATED ALWAYS AS (
        CASE 
            WHEN preco_custo IS NOT NULL AND preco_custo > 0 
            THEN ((preco_venda - preco_custo) / preco_custo * 100)
            ELSE NULL
        END
    ) STORED,                                     -- Calculado automaticamente
    
    -- Estoque
    quantidade_atual INTEGER DEFAULT 0 NOT NULL,
    quantidade_minima INTEGER DEFAULT 0,
    
    -- Controle de IA e Sincronização
    status_ia VARCHAR(50) DEFAULT 'MANUAL',      -- MANUAL, IA_PROCESSADO
    status_validacao VARCHAR(20) DEFAULT 'OK',   -- OK, REVIEW, REJECTED
    versao INTEGER DEFAULT 1,                    -- Controle de versão
    sync_status VARCHAR(20) DEFAULT 'PENDENTE',  -- PENDENTE, SYNCED, CONFLITO
    
    -- Auditoria
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_by UUID REFERENCES auth.users(id),
    updated_by UUID REFERENCES auth.users(id),
    
    CONSTRAINT chk_quantidade_atual CHECK (quantidade_atual >= 0),
    CONSTRAINT chk_preco_venda CHECK (preco_venda >= 0)
);
```

**Campos Calculados:**
- `markup_percentual`: Calculado automaticamente como `((venda - custo) / custo * 100)`

**Status IA:**
- `MANUAL`: Cadastrado manualmente
- `IA_SUGERIDO`: Dados extraídos pela IA, aguardando confirmação
- `IA_PROCESSADO`: Confirmado após análise de IA
- `OFFLINE_ML_KIT`: Processado pelo ML Kit offline

**Status Validação:**
- `OK`: Produto validado e pronto para venda
- `PENDENTE`: Aguardando aprovação (workflow)
- `REVIEW`: Precisa revisão manual
- `REJECTED`: Rejeitado na validação

#### Tabela: `inventory.validation_queue` 🚦 **APPROVAL WORKFLOW**
```sql
CREATE TABLE inventory.validation_queue (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES inventory.products(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id),      -- Quem solicitou
    status VARCHAR(20) DEFAULT 'PENDENTE',
    dados_anteriores JSONB NOT NULL,                       -- Estado anterior
    dados_novos JSONB NOT NULL,                            -- Estado proposto
    observacao TEXT,
    reviewed_by UUID REFERENCES auth.users(id),           -- Quem aprovou/rejeitou
    reviewed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    
    CONSTRAINT chk_validation_status CHECK (status IN ('PENDENTE', 'APROVADO', 'REJEITADO'))
);
```

**Propósito:** Fila de aprovação para mudanças feitas por estoquistas (USER)

**Fluxo:**
1. USER edita produto → Criado registro com `status='PENDENTE'`
2. ADMIN visualiza fila → Vê `dados_anteriores` vs `dados_novos`
3. ADMIN aprova → Produto é atualizado, `status='APROVADO'`
4. ADMIN rejeita → Produto NÃO é atualizado, `status='REJEITADO'`

---

### Schema: `finance` - Movimentações Financeiras

#### Tabela: `finance.stock_movements`
```sql
CREATE TABLE finance.stock_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES inventory.products(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id),
    
    -- Tipo e Detalhes da Movimentação
    tipo_movimento VARCHAR(20) NOT NULL,          -- ENTRADA, VENDA, AJUSTE, PERDA
    quantidade INTEGER NOT NULL,                  -- Positivo/Negativo
    valor_unitario DECIMAL(10,2) NOT NULL,
    valor_total DECIMAL(10,2) GENERATED ALWAYS AS (quantidade * valor_unitario) STORED,
    
    -- Informações Adicionais
    data_movimento TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    observacao TEXT,
    documento_referencia VARCHAR(100),            -- Nota fiscal, recibo
    
    -- Controle de Sincronização
    sync_status VARCHAR(20) DEFAULT 'PENDENTE',
    
    -- Auditoria
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    
    CONSTRAINT chk_tipo_movimento CHECK (tipo_movimento IN ('ENTRADA', 'VENDA', 'AJUSTE', 'PERDA', 'DEVOLUCAO')),
    CONSTRAINT chk_quantidade CHECK (quantidade != 0)
);
```

**Tipos de Movimentação:**
- `ENTRADA`: Compra de mercadoria (quantidade positiva)
- `VENDA`: Venda (quantidade negativa)
- `AJUSTE`: Correção de estoque (inventário)
- `PERDA`: Produto perdido/danificado (quantidade negativa)
- `DEVOLUCAO`: Devolução de cliente (quantidade positiva)

**Valor Total:** Calculado automaticamente como `quantidade × valor_unitario`

---

### Schema: `system` - Auditoria

#### Tabela: `system.audit_logs`
```sql
CREATE TABLE system.audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id),
    tabela VARCHAR(100) NOT NULL,                 -- Nome da tabela afetada
    registro_id UUID NOT NULL,                    -- ID do registro
    acao VARCHAR(50) NOT NULL,                    -- CREATE, UPDATE, DELETE
    dados_anteriores JSONB,                        -- Estado antes
    dados_novos JSONB,                             -- Estado depois
    ip_address INET,
    user_agent TEXT,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_acao CHECK (acao IN ('CREATE', 'UPDATE', 'DELETE', 'LOGIN', 'LOGOUT', 'APROVACAO', 'REJEICAO'))
);
```

**Propósito:** Log completo de todas as ações para compliance e auditoria

---

### Views de Análise

#### `finance.vw_dashboard_lucratividade`
```sql
CREATE OR REPLACE VIEW finance.vw_dashboard_lucratividade AS
SELECT 
    p.id AS product_id,
    p.referencia,
    p.descricao AS produto,
    p.marca,
    c.nome AS categoria,
    p.quantidade_atual AS estoque_atual,
    p.preco_custo,
    p.preco_venda,
    p.markup_percentual,
    
    -- Custo Total Investido
    CASE 
        WHEN p.preco_custo IS NOT NULL 
        THEN (p.quantidade_atual * p.preco_custo)
        ELSE 0
    END AS custo_total_estoque,
    
    -- Valor Potencial de Venda
    (p.quantidade_atual * p.preco_venda) AS valor_potencial_venda,
    
    -- Lucro Potencial
    CASE 
        WHEN p.preco_custo IS NOT NULL 
        THEN ((p.quantidade_atual * p.preco_venda) - (p.quantidade_atual * p.preco_custo))
        ELSE 0
    END AS lucro_potencial
FROM inventory.products p
LEFT JOIN inventory.categories c ON p.category_id = c.id
WHERE p.deleted_at IS NULL;
```

**Propósito:** Dashboard financeiro com métricas de lucratividade

---

### Índices para Performance

```sql
-- Índices de busca
CREATE INDEX idx_products_referencia ON inventory.products(referencia) WHERE deleted_at IS NULL;
CREATE INDEX idx_products_codigo_barras ON inventory.products(codigo_barras) WHERE deleted_at IS NULL;
CREATE INDEX idx_products_sync_status ON inventory.products(sync_status) WHERE deleted_at IS NULL;

-- Índices de alerta de estoque baixo
CREATE INDEX idx_products_quantidade_minima ON inventory.products(quantidade_atual, quantidade_minima) 
    WHERE deleted_at IS NULL AND quantidade_atual <= quantidade_minima;

-- Índices de validação
CREATE INDEX idx_validation_queue_status ON inventory.validation_queue(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_validation_queue_product ON inventory.validation_queue(product_id) WHERE deleted_at IS NULL;

-- Índices de movimentações
CREATE INDEX idx_stock_movements_product ON finance.stock_movements(product_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_stock_movements_data ON finance.stock_movements(data_movimento DESC) WHERE deleted_at IS NULL;
```

---

## 🔧 Backend - Spring Boot

### Estrutura de Pacotes

```
com.visionstock
├── controller/          # REST Controllers
│   ├── ProductController.java
│   └── ScanController.java
├── service/            # Business Logic
│   ├── ProductService.java
│   ├── ValidationService.java
│   └── GeminiService.java
├── repository/         # Data Access (Spring Data JPA)
│   ├── ProductRepository.java
│   ├── ValidationRequestRepository.java
│   └── StockMovementRepository.java
├── model/              # JPA Entities
│   ├── inventory/
│   │   ├── Product.java
│   │   └── ValidationRequest.java
│   ├── finance/
│   │   └── StockMovement.java
│   ├── auth/
│   │   └── User.java
│   └── enums/
│       ├── UserRole.java
│       ├── ValidationStatus.java
│       └── MovementType.java
├── dto/                # Data Transfer Objects
│   ├── ProductCreateDTO.java
│   ├── ProductResponseDTO.java
│   ├── ProductUpdateDTO.java
│   ├── ProductAdminDTO.java
│   └── ValidationRequestDTO.java
└── exception/          # Custom Exceptions
    ├── ResourceNotFoundException.java
    └── DuplicateProductException.java
```

---

### Modelos (Entities)

#### Product.java
```java
@Entity
@Table(name = "products", schema = "inventory")
@Data
@Builder
public class Product {
    @Id
    private UUID id;
    
    private String referencia;
    private String codigoBarras;
    private String imagemUrl;
    private String descricao;
    private String tamanho;
    private String cor;
    private String marca;
    
    private UUID categoryId;
    
    private BigDecimal precoCusto;
    private BigDecimal precoVenda;
    
    private Integer quantidadeAtual;
    private Integer quantidadeMinima;
    
    private String statusIa;           // MANUAL, IA_PROCESSADO
    private String statusValidacao;    // OK, REVIEW, REJECTED
    
    private Integer versao;
    private String syncStatus;         // PENDENTE, SYNCED, CONFLITO
    
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    
    private UUID createdBy;
    private UUID updatedBy;
}
```

#### ValidationRequest.java
```java
@Entity
@Table(name = "validation_queue", schema = "inventory")
@Data
@Builder
public class ValidationRequest {
    @Id
    private UUID id;
    
    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;
    
    private UUID productId;
    private UUID requestedBy;
    
    @Enumerated(EnumType.STRING)
    private ValidationStatus status;   // PENDING, APPROVED, REJECTED
    
    @Column(columnDefinition = "jsonb")
    private String originalData;       // JSON do estado anterior
    
    @Column(columnDefinition = "jsonb")
    private String newData;            // JSON do estado novo
    
    private String reviewNote;
    private UUID reviewedBy;
    private Instant reviewedAt;
    
    private Instant requestedAt;
    
    // Métodos de transição de estado
    public void approve(UUID adminId) {
        this.status = ValidationStatus.APPROVED;
        this.reviewedBy = adminId;
        this.reviewedAt = Instant.now();
    }
    
    public void reject(UUID adminId) {
        this.status = ValidationStatus.REJECTED;
        this.reviewedBy = adminId;
        this.reviewedAt = Instant.now();
    }
}
```

#### StockMovement.java
```java
@Entity
@Table(name = "stock_movements", schema = "finance")
@Data
@Builder
public class StockMovement {
    @Id
    private UUID id;
    
    private UUID productId;
    private UUID userId;
    
    private String tipoMovimento;      // ENTRADA, VENDA, AJUSTE, PERDA
    private Integer quantidade;
    private BigDecimal valorUnitario;
    
    private Instant dataMovimento;
    private String observacao;
    private String documentoReferencia;
    
    private String syncStatus;
}
```

---

### Enums

#### UserRole.java
```java
public enum UserRole {
    ADMIN,    // Gerente - pode aprovar mudanças, acessa custos
    USER      // Estoquista - precisa aprovação, não vê custos
}
```

#### ValidationStatus.java
```java
public enum ValidationStatus {
    PENDING,   // Aguardando aprovação
    APPROVED,  // Aprovado e aplicado
    REJECTED   // Rejeitado, produto não alterado
}
```

#### MovementType.java
```java
public enum MovementType {
    ENTRADA,    // Compra de mercadoria
    VENDA,      // Venda ao cliente
    AJUSTE,     // Ajuste de inventário
    PERDA,      // Perda ou dano
    DEVOLUCAO   // Devolução de cliente
}
```

---

### Data Transfer Objects (DTOs)

#### ProductResponseDTO.java
```java
@Data
@Builder
public class ProductResponseDTO {
    private UUID id;
    private String referencia;
    private String codigoBarras;
    private String imagemUrl;
    private String descricao;
    private String tamanho;
    private String cor;
    private String marca;
    private UUID categoryId;
    
    private BigDecimal precoVenda;      // SEM preco_custo (oculto de USER)
    
    private Integer quantidadeAtual;
    private Integer quantidadeMinima;
    
    private String statusIa;
    private String statusValidacao;
    private String syncStatus;
    
    private Instant createdAt;
    private Instant updatedAt;
}
```

**❗ Importante:** Este DTO **NÃO** inclui `precoCusto` para proteger informação sensível de estoquistas.

#### ProductAdminDTO.java
```java
@Data
@Builder
public class ProductAdminDTO {
    // Todos os campos de ProductResponseDTO, mais:
    
    private BigDecimal precoCusto;      // Visível apenas para ADMIN
    private BigDecimal markupPercentual;
}
```

#### ValidationRequestDTO.java
```java
@Data
@Builder
public class ValidationRequestDTO {
    private UUID id;
    private UUID productId;
    private String productReferencia;
    private String productDescricao;
    
    private UUID requestedBy;
    private ValidationStatus status;
    
    private JsonNode originalData;      // Estado anterior
    private JsonNode newData;           // Estado proposto
    
    private String changesSummary;      // Ex: "Descrição alterada, Preço aumentado"
    
    private UUID reviewedBy;
    private String reviewNote;
    private Instant reviewedAt;
    private Instant requestedAt;
}
```

---

### Services (Lógica de Negócio)

#### ProductService.java

**Métodos Principais:**

##### `createProduct(ProductCreateDTO dto)`
```java
@Transactional
public ProductResponseDTO createProduct(ProductCreateDTO dto) {
    // 1. Verifica duplicatas (ID, código de barras)
    // 2. Cria entidade Product
    // 3. Se quantidadeInicial > 0: Cria StockMovement (ENTRADA)
    // 4. Salva no banco
    // 5. Retorna ProductResponseDTO
}
```

##### `updateProduct(UUID id, ProductUpdateDTO dto, UserRole role, UUID userId)`
```java
@Transactional
public ProductUpdateResponse updateProduct(UUID id, ProductUpdateDTO dto, UserRole role, UUID userId) {
    Product product = findById(id);
    
    if (role == UserRole.ADMIN) {
        // ADMIN: Atualiza diretamente
        applyChanges(product, dto);
        product.setUpdatedBy(userId);
        productRepository.save(product);
        
        return ProductUpdateResponse.builder()
            .status("UPDATED")
            .product(toDTO(product))
            .build();
            
    } else {
        // USER: Cria ValidationRequest
        ValidationRequest request = validationService
            .createValidationRequest(id, dto, userId);
        
        return ProductUpdateResponse.builder()
            .status("PENDING_APPROVAL")
            .validationRequestId(request.getId())
            .message("Aguardando aprovação do gerente")
            .build();
    }
}
```

**🎯 Strategy Pattern:** A lógica muda completamente baseada no `UserRole`.

---

#### ValidationService.java

**Métodos Principais:**

##### `createValidationRequest(UUID productId, ProductUpdateDTO dto, UUID userId)`
```java
@Transactional
public ValidationRequest createValidationRequest(UUID productId, ProductUpdateDTO dto, UUID userId) {
    Product product = productRepository.findById(productId).orElseThrow();
    
    // Serializa estado atual
    String originalData = serializeProductSnapshot(product);
    
    // Cria cópia temporária com mudanças
    Product updatedCopy = applyChangesToCopy(product, dto);
    String newData = serializeProductSnapshot(updatedCopy);
    
    // Cria request
    ValidationRequest request = ValidationRequest.builder()
        .id(UUID.randomUUID())
        .product(product)
        .productId(productId)
        .requestedBy(userId)
        .status(ValidationStatus.PENDING)
        .originalData(originalData)
        .newData(newData)
        .build();
    
    return validationRequestRepository.save(request);
}
```

##### `approveRequest(UUID requestId, UUID adminId, String reviewNote)`
```java
@Transactional
public Product approveRequest(UUID requestId, UUID adminId, String reviewNote) {
    ValidationRequest request = findById(requestId);
    
    if (!request.isPending()) {
        throw new IllegalStateException("Cannot approve non-pending request");
    }
    
    // 1. Extrai mudanças do JSON
    JsonNode newData = objectMapper.readTree(request.getNewData());
    
    // 2. Aplica ao produto
    Product product = request.getProduct();
    applyChangesToProduct(product, newData);
    product.setUpdatedBy(adminId);
    product = productRepository.save(product);
    
    // 3. Marca request como aprovado
    request.approve(adminId);
    request.setReviewNote(reviewNote);
    validationRequestRepository.save(request);
    
    return product;
}
```

##### `rejectRequest(UUID requestId, UUID adminId, String reviewNote)`
```java
@Transactional
public Product rejectRequest(UUID requestId, UUID adminId, String reviewNote) {
    ValidationRequest request = findById(requestId);
    
    if (!request.isPending()) {
        throw new IllegalStateException("Cannot reject non-pending request");
    }
    
    // Marca como rejeitado (produto NÃO é alterado)
    request.reject(adminId);
    request.setReviewNote(reviewNote);
    validationRequestRepository.save(request);
    
    return request.getProduct();  // Retorna produto inalterado
}
```

---

#### GeminiService.java

**Métodos Principais:**

##### `extractDataFromImage(MultipartFile image)`
```java
public ProductResponseDTO extractDataFromImage(MultipartFile image) {
    try {
        // 1. Converte imagem para Base64
        String base64Image = encodeImageToBase64(image);
        
        // 2. Monta prompt para Gemini
        String prompt = buildSystemInstruction();
        
        // 3. Chama API Gemini
        String geminiResponse = callGeminiAPI(base64Image, prompt);
        
        // 4. Parse da resposta JSON
        ProductResponseDTO dto = parseGeminiResponse(geminiResponse);
        
        // 5. Define status IA
        dto.setStatusIa("IA_SUGERIDO");
        dto.setStatusValidacao("PENDENTE");
        
        return dto;
        
    } catch (Exception e) {
        logger.error("Error extracting data from image", e);
        
        return ProductResponseDTO.builder()
            .statusIa("ERRO_IA")
            .statusValidacao("PENDENTE")
            .build();
    }
}
```

**System Instruction (Prompt para Gemini):**
```
Você é um assistente especializado em análise de etiquetas de vestuário.
Extraia as seguintes informações da imagem:

- descricao: Descrição completa do produto
- tamanho: Tamanho (P, M, G, GG)
- cor: Cor principal
- marca: Marca do produto
- codigoBarras: Código de barras (se visível)
- precoVenda: Preço de venda (se visível)

Retorne APENAS um JSON válido sem markdown:
{
  "descricao": "...",
  "tamanho": "...",
  ...
}
```

---

### Repositories (Acesso a Dados)

#### ProductRepository.java
```java
@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    
    boolean existsByCodigoBarras(String codigoBarras);
    
    Optional<Product> findByReferencia(String referencia);
    
    List<Product> findByStatusValidacao(String status);
    
    @Query("SELECT p FROM Product p WHERE p.quantidadeAtual <= p.quantidadeMinima AND p.deletedAt IS NULL")
    List<Product> findLowStockProducts();
    
    @Query("SELECT p FROM Product p WHERE p.syncStatus = 'PENDENTE' AND p.deletedAt IS NULL")
    List<Product> findPendingSync();
}
```

#### ValidationRequestRepository.java
```java
@Repository
public interface ValidationRequestRepository extends JpaRepository<ValidationRequest, UUID> {
    
    List<ValidationRequest> findByStatusOrderByRequestedAtAsc(ValidationStatus status);
    
    List<ValidationRequest> findByProductIdOrderByRequestedAtDesc(UUID productId);
    
    List<ValidationRequest> findByRequestedBy(UUID userId);
    
    Long countByStatus(ValidationStatus status);
    
    @Query("SELECT vr FROM ValidationRequest vr WHERE vr.status = 'PENDING' AND vr.deletedAt IS NULL")
    List<ValidationRequest> findPendingQueue();
}
```

#### StockMovementRepository.java
```java
@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {
    
    List<StockMovement> findByProductIdOrderByDataMovimentoDesc(UUID productId);
    
    List<StockMovement> findByTipoMovimento(String tipo);
    
    @Query("SELECT SUM(sm.quantidade) FROM StockMovement sm WHERE sm.productId = :productId AND sm.tipoMovimento = 'VENDA'")
    Integer getTotalVendasByProduct(@Param("productId") UUID productId);
}
```

---

## 🌐 APIs REST

### ProductController

#### `POST /api/v1/products`
**Criar novo produto**

**Request:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "referencia": "REF-001",
  "descricao": "Camiseta Polo Azul",
  "tamanho": "M",
  "cor": "Azul",
  "marca": "Nike",
  "codigoBarras": "7891234567890",
  "precoCusto": 45.00,
  "precoVenda": 89.90,
  "quantidadeInicial": 10
}
```

**Response (201 Created):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "referencia": "REF-001",
  "descricao": "Camiseta Polo Azul",
  "tamanho": "M",
  "cor": "Azul",
  "marca": "Nike",
  "codigoBarras": "7891234567890",
  "precoVenda": 89.90,
  "quantidadeAtual": 10,
  "quantidadeMinima": 0,
  "statusIa": "MANUAL",
  "statusValidacao": "OK",
  "syncStatus": "PENDENTE",
  "createdAt": "2026-02-14T18:00:00Z",
  "updatedAt": "2026-02-14T18:00:00Z"
}
```

---

#### `PUT /api/v1/products/{id}?role=USER&userId={uuid}`
**Atualizar produto (com workflow de aprovação)**

**Request (USER):**
```json
{
  "descricao": "Camiseta Polo Verde",
  "cor": "Verde",
  "precoVenda": 94.90
}
```

**Response para USER (202 Accepted):**
```json
{
  "status": "PENDING_APPROVAL",
  "message": "Aguardando aprovação do gerente",
  "validationRequestId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

**Response para ADMIN (200 OK):**
```json
{
  "status": "UPDATED",
  "product": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "descricao": "Camiseta Polo Verde",
    "cor": "Verde",
    "precoVenda": 94.90,
    ...
  }
}
```

---

#### `GET /api/v1/validation`
**Listar fila de validações pendentes (ADMIN apenas)**

**Response (200 OK):**
```json
[
  {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "productId": "550e8400-e29b-41d4-a716-446655440000",
    "productReferencia": "REF-001",
    "productDescricao": "Camiseta Polo Azul",
    "requestedBy": "user-uuid",
    "status": "PENDING",
    "originalData": {
      "descricao": "Camiseta Polo Azul",
      "cor": "Azul",
      "precoVenda": "89.90"
    },
    "newData": {
      "descricao": "Camiseta Polo Verde",
      "cor": "Verde",
      "precoVenda": "94.90"
    },
    "changesSummary": "Descrição alterada, Cor alterada, Preço alterado",
    "requestedAt": "2026-02-14T18:30:00Z"
  }
]
```

---

#### `POST /api/v1/validation/{id}/approve`
**Aprovar solicitação de validação**

**Request:**
```json
{
  "adminId": "admin-uuid",
  "reviewNote": "Alteração aprovada conforme solicitado"
}
```

**Response (200 OK):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "descricao": "Camiseta Polo Verde",
  "cor": "Verde",
  "precoVenda": 94.90,
  "updatedBy": "admin-uuid",
  ...
}
```

---

#### `POST /api/v1/validation/{id}/reject`
**Rejeitar solicitação de validação**

**Request:**
```json
{
  "adminId": "admin-uuid",
  "reviewNote": "Preço muito alto, não corresponde ao mercado"
}
```

**Response (200 OK):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "descricao": "Camiseta Polo Azul",
  "cor": "Azul",
  "precoVenda": 89.90,
  ...
}
```
*Produto permanece inalterado*

---

#### `GET /api/v1/products/{id}/validations`
**Histórico de validações de um produto**

**Response (200 OK):**
```json
[
  {
    "id": "validation-uuid-1",
    "status": "APPROVED",
    "changesSummary": "Preço aumentado",
    "requestedBy": "user-uuid",
    "reviewedBy": "admin-uuid",
    "reviewNote": "Ajuste conforme inflação",
    "requestedAt": "2026-02-10T10:00:00Z",
    "reviewedAt": "2026-02-10T14:30:00Z"
  },
  {
    "id": "validation-uuid-2",
    "status": "REJECTED",
    "changesSummary": "Descrição alterada",
    "requestedBy": "user-uuid",
    "reviewedBy": "admin-uuid",
    "reviewNote": "Descrição incorreta",
    "requestedAt": "2026-02-12T09:00:00Z",
    "reviewedAt": "2026-02-12T09:15:00Z"
  }
]
```

---

### ScanController

#### `POST /api/v1/scan`
**Extrair dados de produto via foto de etiqueta**

**Request (multipart/form-data):**
```
POST /api/v1/scan
Content-Type: multipart/form-data

image: [arquivo de imagem]
```

**Response (200 OK):**
```json
{
  "descricao": "Camiseta Regata Branca",
  "tamanho": "G",
  "cor": "Branco",
  "marca": "Adidas",
  "codigoBarras": "7898765432109",
  "precoVenda": 79.90,
  "statusIa": "IA_SUGERIDO",
  "statusValidacao": "PENDENTE"
}
```

**Response em caso de erro (200 OK com status de erro):**
```json
{
  "statusIa": "ERRO_IA",
  "statusValidacao": "PENDENTE",
  "descricao": null,
  "tamanho": null,
  ...
}
```

---

## 🤖 Integração com IA

### Google Gemini 2.5 Flash

**Endpoint:** `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent`

**Configuração:**
```properties
gemini.api.key=${GEMINI_API_KEY}
gemini.api.model=gemini-2.5-flash
gemini.api.url=https://generativelanguage.googleapis.com/v1beta/models
```

### Fluxo de Extração de Dados

```
┌──────────────┐
│  1. Upload   │  Usuário tira foto da etiqueta no app
│  de Imagem   │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ 2. Encoding  │  Backend converte imagem para Base64
│   Base64     │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ 3. Prompt    │  Monta system instruction + imagem
│  Engineering │  "Extraia dados de vestuário..."
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ 4. Gemini    │  POST para API Gemini com imagem
│    API       │  GEMINI_API_KEY como autenticação
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ 5. Parse     │  Extrai JSON da resposta
│  Response    │  {"descricao": "...", "tamanho": "..."}
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ 6. DTO       │  Cria ProductResponseDTO
│ Population   │  com statusIa="IA_SUGERIDO"
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ 7. Frontend  │  Retorna para usuário confirmar
│ Confirmation │  ou editar antes de salvar
└──────────────┘
```

### Rate Limiting e Fallback

**Tratamento de Erros:**
- ❌ `429 Too Many Requests` → Retorna `ERRO_IA`, usuário cadastra manualmente
- ❌ `400 Bad Request` → Retorna `ERRO_IA`
- ❌ Timeout → Retorna `ERRO_IA`
- ❌ JSON inválido → Retorna `ERRO_IA`

**Fallback Offline (Futuro):**
- Google ML Kit rodando no dispositivo
- statusIa = `OFFLINE_ML_KIT`
- Menor acurácia mas funciona sem internet

---

## 🚦 Fluxo de Approval Workflow

### Visão Geral

O **Approval Workflow** é um **guard-rail** que impede estoquistas (USER) de fazerem mudanças não autorizadas em produtos, enquanto permite que gerentes (ADMIN) façam alterações imediatas.

### Diagrama de Estados

```
┌─────────────────────────────────────────────────────────┐
│              FLUXO DE ATUALIZAÇÃO DE PRODUTO            │
└─────────────────────────────────────────────────────────┘

Usuario tenta atualizar produto
        │
        ▼
     ┌────────┐
     │ Role?  │
     └───┬────┘
         │
    ┌────┴────┐
    │         │
  ADMIN      USER
    │         │
    ▼         ▼
┌──────┐  ┌──────────────────┐
│ DIRETO│  │ CRIA VALIDATION  │
│ UPDATE│  │     REQUEST      │
└───┬────┘  └────────┬─────────┘
    │               │
    │               ▼
    │       ┌────────────────┐
    │       │ status=PENDING │
    │       │ produto NÃO    │
    │       │ atualizado     │
    │       └────────┬────────┘
    │               │
    │         ┌─────┴─────┐
    │         │   ADMIN   │
    │         │  REVISA   │
    │         └─────┬─────┘
    │               │
    │       ┌───────┴───────┐
    │       │               │
    │    APROVA         REJEITA
    │       │               │
    │       ▼               ▼
    │  ┌─────────┐    ┌──────────┐
    │  │ APLICA  │    │ DESCARTA │
    │  │ MUDANÇAS│    │ MUDANÇAS │
    │  └─────────┘    └──────────┘
    │       │               │
    └───────┴───────────────┴──→ FIM
```

### Cenários Práticos

#### Cenário 1: ADMIN atualiza preço
```
1. ADMIN loga no sistema
2. Busca produto "Camiseta Polo"
3. Altera precoVenda de R$ 89,90 → R$ 94,90
4. ✅ Produto atualizado IMEDIATAMENTE
5. Histórico registra: updated_by = admin_uuid
```

#### Cenário 2: USER atualiza descrição
```
1. USER (estoquista) loga no sistema
2. Busca produto "Camiseta Polo Verde"
3. Corrige descrição: "Verde" → "Verde Água"
4. ⏳ ValidationRequest criada com status=PENDING
5. ❌ Produto NÃO é atualizado ainda
6. Usuário vê mensagem: "Aguardando aprovação do gerente"
```

#### Cenário 3: ADMIN aprova solicitação
```
1. ADMIN acessa /api/v1/validation
2. Vê lista de solicitações pendentes
3. Analisa mudança: "Verde" → "Verde Água"
4. Aprova com nota: "Correção válida"
5. ✅ Sistema aplica mudança ao produto
6. ValidationRequest atualizado: status=APPROVED
7. Produto.updatedBy = admin_uuid
```

#### Cenário 4: ADMIN rejeita solicitação
```
1. ADMIN acessa /api/v1/validation
2. Vê solicitação: precoVenda R$ 89,90 → R$ 150,00
3. Considera preço excessivo
4. Rejeita com nota: "Preço fora do mercado"
5. ❌ Produto permanece com R$ 89,90
6. ValidationRequest atualizado: status=REJECTED
7. Estoquista é notificado da rejeição
```

---

## ✨ Funcionalidades Principais

### 1. 📦 Gestão de Produtos

**Criar Produto:**
- Cadastro manual ou via IA
- Validação de duplicatas (ID, código de barras)
- Campos obrigatórios: id, descricao, precoVenda, quantidadeInicial
- Automático: markup_percentual calculado
- Criação de movimentação inicial (ENTRADA)

**Atualizar Produto:**
- ADMIN: Atualização imediata
- USER: Cria solicitação de validação
- Campos editáveis: descricao, cor, tamanho, precoVenda
- Auditoria: updatedBy, updatedAt

**Consultar Produto:**
- Por ID, referência ou código de barras
- Lista com filtros (categoria, status_validacao)
- DTO diferenciado por role (USER não vê precoCusto)

**Alertas de Estoque:**
- Query automática: `quantidade_atual <= quantidade_minima`
- Dashboard de produtos com estoque baixo

### 2. 🤖 Análise de IA

**Scanner de Etiquetas:**
- Upload de foto → Base64
- Chamada Gemini API
- Extração de: descricao, tamanho, cor, marca, codigoBarras, precoVenda
- statusIa = "IA_SUGERIDO"
- Confirmação manual antes de salvar

**Tratamento de Erros:**
- Fallback para cadastro manual se IA falhar
- statusIa = "ERRO_IA"
- Log de erros para análise

### 3. 🚦 Workflow de Validação

**Fila de Aprovação:**
- Lista pendências por ordem de criação
- Visualização side-by-side (antes/depois)
- Resumo de mudanças: "Descrição alterada, Preço aumentado"

**Aprovação:**
- ADMIN aplica mudanças ao produto
- Registro de quem aprovou e quando
- Nota de revisão opcional

**Rejeição:**
- Produto permanece inalterado
- Registro de motivo da rejeição
- Notificação ao solicitante

**Histórico:**
- Todas as validações de um produto
- Auditoria completa de alterações

### 4. 💰 Gestão Financeira

**Movimentações de Estoque:**
- ENTRADA: Compras, devoluções
- VENDA: Vendas ao cliente
- AJUSTE: Correções de inventário
- PERDA: Produtos perdidos/danificados

**Cálculos Automáticos:**
- valor_total = quantidade × valor_unitario
- markup_percentual = ((venda - custo) / custo) × 100

**Dashboards:**
- View `vw_dashboard_lucratividade`
- Custo total em estoque
- Valor potencial de venda
- Lucro potencial por produto

### 5. 🔐 Controle de Acesso

**Roles:**
- **ADMIN** (Gerente):
  - Atualiza produtos diretamente
  - Aprova/rejeita solicitações
  - Acessa precoCusto e markup
  - Visualiza dashboards financeiros

- **USER** (Estoquista):
  - Cadastra produtos
  - Solicita alterações (requer aprovação)
  - NÃO vê precoCusto
  - Consulta estoque

### 6. 🔄 Sincronização Offline-First

**Estados de Sync:**
- `PENDENTE`: Criado offline, aguarda sync
- `SYNCED`: Sincronizado com servidor
- `CONFLITO`: Conflito detectado, requer resolução manual

**Fluxo:**
1. Operação offline → sync_status = PENDENTE
2. Conexão restaurada → Tentativa de sync
3. Sucesso → sync_status = SYNCED
4. Conflito → sync_status = CONFLITO, notifica usuário

### 7. 📊 Auditoria e Logs

**Audit Trail:**
- Todas as operações registradas em `system.audit_logs`
- Quem fez, quando, o quê, de onde (IP)
- Estado anterior vs. estado novo (JSON)

**Eventos Auditados:**
- CREATE, UPDATE, DELETE
- LOGIN, LOGOUT
- APROVACAO, REJEICAO

---

## 🛠️ Tecnologias Utilizadas

### Backend

| Tecnologia | Versão | Uso |
|------------|--------|-----|
| **Java** | 21 | Linguagem principal |
| **Spring Boot** | 3.2.5 | Framework backend |
| **Spring Data JPA** | 3.2.5 | ORM e acesso a dados |
| **Hibernate** | 6.4.x | Implementação JPA |
| **PostgreSQL** | 15+ | Banco de dados relacional |
| **Lombok** | 1.18.x | Redução de boilerplate |
| **Maven** | 3.8+ | Gerenciamento de dependências |
| **Jackson** | 2.15.x | Serialização JSON |

### APIs Externas

| Serviço | Versão | Uso |
|---------|--------|-----|
| **Google Gemini** | 2.5 Flash | IA para OCR de etiquetas |
| **Google ML Kit** | - | Fallback offline (futuro) |

### Ferramentas de Desenvolvimento

| Ferramenta | Uso |
|------------|-----|
| **JUnit 5** | Testes unitários |
| **Mockito** | Mocking para testes |
| **Spring Test** | Testes de integração |
| **Swagger/OpenAPI** | Documentação de API |
| **dotenv-java** | Gestão de variáveis de ambiente |
| **Git** | Controle de versão |

### Infraestrutura

| Componente | Tecnologia |
|------------|------------|
| **Banco de Dados** | PostgreSQL 15+ com Multi-Schema |
| **Servidor de Aplicação** | Tomcat Embedded (Spring Boot) |
| **Autenticação** | JWT (futuro) |
| **Deploy** | Docker (futuro) |

---

## 📐 Diagramas

### Diagrama Entidade-Relacionamento (ER)

```
┌─────────────────────────────────────────────────────────────────┐
│                         SCHEMA: AUTH                             │
└─────────────────────────────────────────────────────────────────┘

    ┌─────────────┐
    │   roles     │
    │──────────────│
    │ id (PK)     │
    │ name        │
    │ permissions │
    └──────┬──────┘
           │
           │ 1:N
           │
    ┌──────▼──────┐
    │   users     │
    │─────────────│
    │ id (PK)     │
    │ nome        │
    │ email       │
    │ senha_hash  │
    │ role        │
    │ role_id(FK) │
    └──────┬──────┘
           │
           │ created_by/updated_by
           │
┌──────────┴──────────────────────────────────────────────────────┐
│                       SCHEMA: INVENTORY                          │
└──────────────────────────────────────────────────────────────────┘

    ┌─────────────┐
    │ categories  │
    │─────────────│
    │ id (PK)     │
    │ nome        │
    │ parent_id   │◄──┐ (self-ref)
    └──────┬──────┘   │
           │          │
           │ 1:N      │
           │          │
    ┌──────▼──────────┴───┐
    │      products       │
    │─────────────────────│
    │ id (PK)             │
    │ referencia          │
    │ codigo_barras       │
    │ descricao           │
    │ tamanho             │
    │ cor                 │
    │ marca               │
    │ category_id (FK)    │
    │ preco_custo         │
    │ preco_venda         │
    │ markup_percentual   │◄─── (calculado)
    │ quantidade_atual    │
    │ quantidade_minima   │
    │ status_ia           │
    │ status_validacao    │
    │ versao              │
    │ sync_status         │
    │ created_by(FK)      │
    │ updated_by(FK)      │
    └──────┬──────────────┘
           │
           │ 1:N
           │
    ┌──────▼─────────────┐
    │ validation_queue   │
    │────────────────────│
    │ id (PK)            │
    │ product_id (FK)    │
    │ user_id (FK)       │
    │ status             │
    │ dados_anteriores   │◄─── JSONB
    │ dados_novos        │◄─── JSONB
    │ reviewed_by (FK)   │
    └────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                       SCHEMA: FINANCE                            │
└─────────────────────────────────────────────────────────────────┘

    ┌────────────────────┐
    │  stock_movements   │
    │────────────────────│
    │ id (PK)            │
    │ product_id (FK)    │──┐
    │ user_id (FK)       │  │
    │ tipo_movimento     │  │
    │ quantidade         │  │
    │ valor_unitario     │  │
    │ valor_total        │◄─┼─ (calculado)
    │ data_movimento     │  │
    │ documento_ref      │  │
    └────────────────────┘  │
                            │
                    Referencia products

┌─────────────────────────────────────────────────────────────────┐
│                       SCHEMA: SYSTEM                             │
└─────────────────────────────────────────────────────────────────┘

    ┌────────────────────┐
    │   audit_logs       │
    │────────────────────│
    │ id (PK)            │
    │ user_id (FK)       │
    │ tabela             │
    │ registro_id        │
    │ acao               │
    │ dados_anteriores   │◄─── JSONB
    │ dados_novos        │◄─── JSONB
    │ ip_address         │
    │ timestamp          │
    └────────────────────┘
```

### Diagrama de Sequência - Approval Workflow

```
USER          Frontend      ProductController  ProductService  ValidationService  Database
  │               │                 │                 │                │            │
  │──(1) Edit────▶│                 │                 │                │            │
  │               │                 │                 │                │            │
  │               │──(2) PUT────────▶│                 │                │            │
  │               │   /products/{id} │                 │                │            │
  │               │   ?role=USER     │                 │                │            │
  │               │                  │                 │                │            │
  │               │                  │──(3)────────────▶│                │            │
  │               │                  │ updateProduct() │                │            │
  │               │                  │                 │                │            │
  │               │                  │                 │──(4)───────────▶│            │
  │               │                  │                 │ createValidation│            │
  │               │                  │                 │     Request()   │            │
  │               │                  │                 │                 │            │
  │               │                  │                 │                 │──(5) INSERT──▶
  │               │                  │                 │                 │  validation_queue
  │               │                  │                 │                 │            │
  │               │                  │                 │                 │◄─(6) id────┤
  │               │                  │                 │                 │            │
  │               │                  │                 │◄──(7) return───┤            │
  │               │                  │                 │  ValidationReq  │            │
  │               │                  │                 │                 │            │
  │               │                  │◄──(8) return───┤                 │            │
  │               │                  │ PENDING_APPROVAL                 │            │
  │               │                  │                                  │            │
  │               │◄─(9) 202 Accepted┤                                  │            │
  │               │   {status:PENDING│                                  │            │
  │               │   validationId: │                                  │            │
  │◄─(10) Message┤   "a1b2..."}    │                                  │            │
  │ "Aguardando   │                 │                                  │            │
  │  aprovação"   │                 │                                  │            │
  │               │                 │                                  │            │
                  
═══════════════════ Tempo passa, ADMIN revisa ════════════════════

ADMIN         Frontend      ProductController  ValidationService  ProductService  Database
  │               │                 │                 │                │            │
  │──(11) Review─▶│                 │                 │                │            │
  │               │                 │                 │                │            │
  │               │──(12) GET───────▶│                 │                │            │
  │               │   /validation    │                 │                │            │
  │               │                  │                 │                │            │
  │               │                  │──(13)───────────▶│                │            │
  │               │                  │ getPending()    │                │            │
  │               │                  │                 │                │            │
  │               │                  │                 │──(14) SELECT──▶│            │
  │               │                  │                 │                │            │
  │               │                  │                 │◄─(15) list─────┤            │
  │               │                  │                 │                │            │
  │               │                  │◄──(16) return───┤                │            │
  │               │                  │   [...pending]  │                │            │
  │               │                  │                 │                │            │
  │               │◄─(17) 200 OK────┤                 │                │            │
  │               │   [{id,original, │                 │                │            │
  │◄──(18) Show───┤    new,summary}]│                 │                │            │
  │    Queue      │                  │                 │                │            │
  │               │                  │                 │                │            │
  │──(19) Approve─▶│                 │                 │                │            │
  │               │                  │                 │                │            │
  │               │──(20) POST───────▶│                 │                │            │
  │               │/validation/{id}/ │                 │                │            │
  │               │     approve      │                 │                │            │
  │               │                  │                 │                │            │
  │               │                  │──(21)───────────▶│                │            │
  │               │                  │ approveRequest()│                │            │
  │               │                  │                 │                │            │
  │               │                  │                 │──(22)──────────▶│            │
  │               │                  │                 │ applyChanges() │            │
  │               │                  │                 │                │            │
  │               │                  │                 │                │──(23) BEGIN──▶
  │               │                  │                 │                │            │
  │               │                  │                 │                │──(24) UPDATE─▶
  │               │                  │                 │                │  products   │
  │               │                  │                 │                │            │
  │               │                  │                 │                │──(25) UPDATE─▶
  │               │                  │                 │                │  validation_│
  │               │                  │                 │                │  queue      │
  │               │                  │                 │                │  (APPROVED) │
  │               │                  │                 │                │            │
  │               │                  │                 │                │──(26) COMMIT─▶
  │               │                  │                 │                │            │
  │               │                  │                 │◄──(27) product─┤            │
  │               │                  │                 │                │            │
  │               │                  │◄──(28) return───┤                │            │
  │               │                  │    updated product              │            │
  │               │                  │                                 │            │
  │               │◄─(29) 200 OK────┤                                 │            │
  │               │   {id,descricao. │                                 │            │
  │◄─(30) Success┤    ... updated}  │                                 │            │
  │               │                  │                                 │            │
```

---

## 🎯 Próximos Passos

### Backlog de Funcionalidades

1. **Autenticação JWT**
   - Login/Logout
   - Refresh tokens
   - Controle de sessão

2. **Categorias Hierárquicas**
   - CRUD completo
   - Árvore de categorias
   - Navegação por categoria

3. **Relatórios Avançados**
   - Vendas por período
   - Curva ABC
   - Produtos mais vendidos
   - Análise de margem

4. **Notificações**
   - Push notifications
   - Email para aprovações pendentes
   - Alertas de estoque baixo

5. **Histórico de Preços**
   - Rastreamento de mudanças de preço
   - Gráficos de evolução

6. **Exportação de Dados**
   - CSV, Excel, PDF
   - Relatórios customizados

7. **Dashboard Web**
   - Interface web para gerentes
   - Gráficos interativos
   - Analytics em tempo real

---

## 📚 Referências

- [Documentação do Banco de Dados](../database/README.md)
- [Quick Start Guide](../database/QUICKSTART.md)
- [API REST](./API.md)
- [Approval Workflow](./APPROVAL_WORKFLOW.md)
- [Testes do Approval Workflow](./APPROVAL_WORKFLOW_TESTS.md)
- [Integração Gemini](./GEMINI_INTEGRATION.md)
- [Guia de Testes](./TESTING.md)
- [Atualização da Suite de Testes](./TEST_SUITE_UPDATE.md)
- [Troubleshooting](./TROUBLESHOOTING.md)
- [Changelog](./CHANGELOG.md)

---

## 📄 Licença

© 2026 VisionStock. Todos os direitos reservados.

---

**Última Atualização:** 14 de fevereiro de 2026  
**Versão do Documento:** 1.0.0  
**Autor:** Equipe VisionStock
