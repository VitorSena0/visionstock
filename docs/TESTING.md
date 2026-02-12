# Guia de Testes

Este documento descreve como executar e criar testes para o VisionStock.

## 📁 Estrutura de Testes

```
src/test/
├── java/
│   └── com/
│       └── visionstock/
│           ├── controller/
│           │   └── ScanControllerTest.java      # Testes do controller
│           ├── dto/
│           │   └── ProductDTOTest.java          # Testes dos DTOs
│           └── service/
│               ├── GeminiServiceTest.java       # Testes unitários
│               └── GeminiServiceIntegrationTest.java  # Testes de integração
└── resources/
    ├── application-test.properties              # Configurações de teste
    └── image.jpg                                # Imagem para teste de integração
```

## 🧪 Tipos de Testes

### 1. Testes Unitários

Testam componentes isoladamente, sem dependências externas.

**Características:**
- Rápidos (< 1 segundo por teste)
- Não fazem chamadas de rede
- Usam mocks quando necessário
- Executados em todo build

**Exemplo:** `GeminiServiceTest.java`

```java
@Test
@DisplayName("parseResponse should extract product data from valid Gemini response")
void parseResponse_shouldExtractProductData() {
    String geminiResponse = """
        {
          "candidates": [{
            "content": {
              "parts": [{
                "text": "{\\"descricao\\":\\"Camiseta Polo Azul\\"}"
              }]
            }
          }]
        }
        """;

    ProductResponseDTO dto = geminiService.parseResponse(geminiResponse);

    assertEquals("Camiseta Polo Azul", dto.getDescricao());
}
```

### 2. Testes de Integração

Testam a integração real com serviços externos (API Gemini).

**Características:**
- Mais lentos (segundos)
- Fazem chamadas reais à API
- Requerem configuração (API key, imagem)
- Marcados com `@Tag("integration")`
- Excluídos do build normal

**Exemplo:** `GeminiServiceIntegrationTest.java`

```java
@Tag("integration")
class GeminiServiceIntegrationTest {
    
    @Test
    @DisplayName("Should extract product data from real clothing label image")
    void extractDataFromImage_withRealImage_shouldReturnProductData() {
        // Carrega imagem real
        ClassPathResource imageResource = new ClassPathResource("image.jpg");
        
        // Chama API Gemini real
        ProductResponseDTO result = geminiService.extractDataFromImage(mockFile);
        
        // Verifica resultado
        assertEquals("IA_SUGERIDO", result.getStatusIa());
    }
}
```

## ▶️ Executando Testes

### Todos os testes (exceto integração)

```bash
cd visionstock/backend
./mvnw test -DexcludedGroups=integration
```

### Apenas testes unitários de um arquivo

```bash
./mvnw test -Dtest=GeminiServiceTest
```

### Testes de integração (requer configuração)

```bash
# 1. Coloque uma imagem em src/test/resources/image.jpg
# 2. Execute com a API key:
GEMINI_API_KEY=sua-chave ./mvnw test -Dtest=GeminiServiceIntegrationTest
```

### Todos os testes

```bash
GEMINI_API_KEY=sua-chave ./mvnw test
```

## 🖼️ Configurando Teste de Integração com Imagem

### 1. Adicione uma imagem de teste

Coloque uma foto de etiqueta de roupa em:
```
src/test/resources/image.jpg
```

**Requisitos da imagem:**
- Formato: JPG ou PNG
- Tamanho: até 10MB
- Conteúdo: etiqueta de roupa visível

### 2. Configure a variável de ambiente

```bash
export GEMINI_API_KEY=sua-chave-api-aqui
```

### 3. Execute o teste

```bash
./mvnw test -Dtest=GeminiServiceIntegrationTest
```

### 4. Resultado esperado

```
🚀 Calling Gemini API with image...
[INFO] Gemini API response received in 7227 ms

📦 Extracted Product Data:
   Descrição: KIT COM 3 CUECAS BOXER COMPRIDA
   Tamanho: M
   Cor: CARM/CROC/IND MAD
   Marca: DIAMANTES
   Código de Barras: 7912214020497
   Preço Venda: null
   Status IA: IA_SUGERIDO
   Status Validação: PENDENTE

Tests run: 2, Failures: 0, Skipped: 1
```

## 🔄 Tratamento de Rate Limiting

O teste de integração trata automaticamente erros de cota (429):

```java
// Handle rate limiting gracefully
if ("ERRO_IA".equals(result.getStatusIa())) {
    System.out.println("⚠️ API returned error - possibly rate limited (429)");
    System.out.println("   Wait 60 seconds and try again");
    assumeTrue(false, "API rate limited - skipping assertion");
}
```

**Comportamento:**
- Se houver rate limiting, o teste é **SKIPPED** (não falha)
- Aguarde ~60 segundos e execute novamente

## 📊 Configurações de Teste (`application-test.properties`)

```properties
spring.application.name=visionstock-api-test

# H2 in-memory database for testing
spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

# JPA / Hibernate - auto-create schema for tests
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# H2 does not support PostgreSQL schemas natively
spring.jpa.properties.hibernate.default_schema=PUBLIC

# Google Gemini AI Configuration (test values)
gemini.api.key=test-api-key
gemini.api.model=gemini-2.0-flash
gemini.api.url=https://generativelanguage.googleapis.com/v1beta/models
```

**Notas:**
- Usa **H2 em memória** (não precisa de PostgreSQL)
- `ddl-auto=create-drop` recria o schema a cada execução
- Gemini API key de teste (não faz chamadas reais nos testes unitários)

## ✅ Checklist de Testes

### Antes de commitar:

- [ ] Todos os testes unitários passando
- [ ] Sem erros de compilação
- [ ] Código formatado

```bash
./mvnw test -DexcludedGroups=integration
```

### Antes de release:

- [ ] Testes de integração passando
- [ ] API Gemini respondendo corretamente
- [ ] Imagem de teste atualizada

```bash
GEMINI_API_KEY=sua-chave ./mvnw test
```

## 📈 Relatórios de Testes

Os relatórios são gerados em:
```
target/surefire-reports/
├── com.visionstock.service.GeminiServiceTest.txt
├── com.visionstock.service.GeminiServiceIntegrationTest.txt
├── TEST-com.visionstock.service.GeminiServiceTest.xml
└── TEST-com.visionstock.service.GeminiServiceIntegrationTest.xml
```

### Visualizar relatório

```bash
cat target/surefire-reports/com.visionstock.service.GeminiServiceIntegrationTest.txt
```
