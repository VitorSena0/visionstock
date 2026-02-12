# Documentação da API REST

Este documento descreve os endpoints disponíveis na API do VisionStock.

## 🌐 Base URL

```
http://localhost:8080
```

## 📚 Documentação Interativa

Após iniciar a aplicação, acesse:

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

## 🔍 Endpoints

### POST /api/scan

Analisa uma imagem de etiqueta de roupa e extrai dados do produto usando IA.

#### Request

- **Content-Type:** `multipart/form-data`
- **Parâmetro:** `file` - Imagem da etiqueta (JPG, PNG)

#### Exemplo com cURL

```bash
curl -X POST http://localhost:8080/api/scan \
  -H "Content-Type: multipart/form-data" \
  -F "file=@etiqueta.jpg"
```

#### Response (200 OK)

```json
{
  "descricao": "KIT COM 3 CUECAS BOXER COMPRIDA",
  "tamanho": "M",
  "cor": "CARM/CROC/IND MAD",
  "marca": "DIAMANTES",
  "codigoBarras": "7912214020497",
  "precoVenda": null,
  "statusIa": "IA_SUGERIDO",
  "statusValidacao": "PENDENTE"
}
```

#### Campos da Resposta

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `descricao` | String | Descrição do produto extraída da etiqueta |
| `tamanho` | String | Tamanho (P, M, G, GG, 38, 40, etc.) |
| `cor` | String | Cor do produto |
| `marca` | String | Marca do produto |
| `codigoBarras` | String | Código de barras (se visível) |
| `precoVenda` | BigDecimal | Preço de venda (se visível) |
| `statusIa` | String | Status da análise: `IA_SUGERIDO` ou `ERRO_IA` |
| `statusValidacao` | String | Status de validação: `PENDENTE` |

#### Response de Erro

**Erro de IA (parsing falhou):**
```json
{
  "descricao": null,
  "tamanho": null,
  "cor": null,
  "marca": null,
  "codigoBarras": null,
  "precoVenda": null,
  "statusIa": "ERRO_IA",
  "statusValidacao": "PENDENTE"
}
```

**Erro de requisição (400):**
```json
{
  "timestamp": "2026-02-12T20:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Required request part 'file' is not present",
  "path": "/api/scan"
}
```

#### Limitações

- **Tamanho máximo do arquivo:** 10MB
- **Formatos suportados:** JPEG, PNG
- **Rate limit da API Gemini:** 15 requisições/minuto (plano gratuito)

---

## 📊 DTOs

### ProductResponseDTO

DTO retornado pelo endpoint `/api/scan`. Contém dados básicos visíveis para todos os usuários.

```java
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
    private BigDecimal precoVenda;
    private Integer quantidadeAtual;
    private Integer quantidadeMinima;
    private String statusIa;
    private String statusValidacao;
    private String syncStatus;
    private Instant createdAt;
    private Instant updatedAt;
}
```

### ProductAdminDTO

DTO com dados completos para administradores. Inclui informações financeiras sensíveis.

```java
public class ProductAdminDTO {
    // ... todos os campos de ProductResponseDTO ...
    private BigDecimal precoCusto;      // Custo do produto
    private BigDecimal markupPercentual; // Margem de lucro calculada
    private Integer versao;
    private UUID createdBy;
    private UUID updatedBy;
}
```

**Nota:** O campo `markupPercentual` é calculado automaticamente:
```
markup = ((precoVenda - precoCusto) / precoCusto) * 100
```

---

## 🔐 Autenticação (Planejado)

A autenticação JWT será implementada em versões futuras:

```
Authorization: Bearer <token>
```

### Roles planejados

| Role | Permissões |
|------|------------|
| `VENDEDOR` | Visualizar produtos, registrar vendas |
| `ESTOQUISTA` | Adicionar/editar produtos, controle de estoque |
| `GERENTE` | Acesso total, incluindo dados financeiros |

---

## 📝 Exemplo de Integração

### JavaScript/Fetch

```javascript
const formData = new FormData();
formData.append('file', imageFile);

const response = await fetch('http://localhost:8080/api/scan', {
  method: 'POST',
  body: formData
});

const product = await response.json();
console.log(product.descricao); // "Camiseta Polo"
```

### Dart/Flutter

```dart
import 'package:dio/dio.dart';

final dio = Dio();
final formData = FormData.fromMap({
  'file': await MultipartFile.fromFile(imagePath),
});

final response = await dio.post(
  'http://localhost:8080/api/scan',
  data: formData,
);

final product = response.data;
print(product['descricao']); // "Camiseta Polo"
```

### Python/Requests

```python
import requests

files = {'file': open('etiqueta.jpg', 'rb')}
response = requests.post('http://localhost:8080/api/scan', files=files)

product = response.json()
print(product['descricao'])  # "Camiseta Polo"
```

---

## 📈 Monitoramento

### Logs de requisição

Cada requisição ao endpoint `/api/scan` é logada:

```
INFO: Scan request received: file=IMG_20260211_203625.jpg, size=3045536 bytes
INFO: Gemini API response received in 7227 ms
```

### Métricas (Planejado)

- Tempo médio de resposta da API Gemini
- Taxa de sucesso/erro da IA
- Número de requisições por período
