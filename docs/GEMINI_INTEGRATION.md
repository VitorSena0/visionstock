# Integração com Google Gemini AI

Este documento descreve a integração do VisionStock com a API Google Gemini para análise de imagens de etiquetas de produtos.

## 📖 Visão Geral

O VisionStock utiliza o **Google Gemini AI** para extrair automaticamente informações de etiquetas de roupas a partir de imagens. O usuário fotografa a etiqueta e a IA retorna os dados estruturados do produto.

## 🔑 Obtenção da Chave de API

### 1. Acessar o Google AI Studio

1. Acesse [Google AI Studio](https://aistudio.google.com/)
2. Faça login com sua conta Google
3. Clique em "Get API Key"
4. Crie uma nova chave ou use uma existente

### 2. Configurar a chave no projeto

Adicione no arquivo `.env`:

```properties
GEMINI_API_KEY=AIzaSy...sua-chave-aqui
```

## 🔄 Evolução dos Modelos Gemini

### Histórico de mudanças

| Data | Modelo | Status |
|------|--------|--------|
| 2024 | `gemini-1.5-flash` | ❌ Descontinuado |
| 2026 | `gemini-2.0-flash` | ❌ Não existe |
| 2025 | `gemini-2.5-flash`| ✅ **Atual** |

### Problema encontrado

Durante o desenvolvimento, o modelo `gemini-1.5-flash` retornou erro 404:

```json
{
  "error": {
    "code": 404,
    "message": "models/gemini-1.5-flash is not found for API version v1beta",
    "status": "NOT_FOUND"
  }
}
```

### Solução

Atualizar para `gemini-2.0-flash` no `application.properties`:

```properties
gemini.api.model=gemini-2.5-flash
```

## 🌐 URLs da API

### URL Base

```
https://generativelanguage.googleapis.com/v1beta/models
```

### Endpoint de geração de conteúdo

```
POST /{model}:generateContent?key={api_key}
```

**Exemplo completo:**
```
POST https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=AIzaSy...
```

### ⚠️ Atenção: v1 vs v1beta

- ❌ `v1` - Não suporta todos os modelos
- ✅ `v1beta` - Versão recomendada com suporte completo

## 📊 Limites de Cota (Plano Gratuito)

| Métrica | Limite |
|---------|--------|
| Requisições por minuto | 15 |
| Requisições por dia | 1.500 |
| Tokens por minuto | 1.000.000 |

### Erro de cota excedida (429)

```json
{
  "error": {
    "code": 429,
    "message": "You exceeded your current quota...",
    "status": "RESOURCE_EXHAUSTED"
  }
}
```

**Solução:** Aguarde o tempo indicado (geralmente 60 segundos) e tente novamente.

## 🏗️ Arquitetura da Integração

### Fluxo de dados

```
┌──────────┐    ┌───────────────┐    ┌─────────────┐    ┌────────────┐
│  Cliente │───▶│ ScanController│───▶│GeminiService│───▶│ Gemini API │
│ (imagem) │    │               │    │             │    │            │
└──────────┘    └───────────────┘    └─────────────┘    └────────────┘
                       │                    │                  │
                       │                    │                  │
                       ▼                    ▼                  ▼
               ProductResponseDTO ◀── Parse JSON ◀─── Resposta AI
```

### Componentes

1. **ScanController** - Recebe a imagem via multipart/form-data
2. **GeminiService** - Processa a imagem e chama a API Gemini
3. **ProductResponseDTO** - Estrutura de dados retornada ao cliente

## 📝 GeminiService - Implementação

### System Instruction (Prompt)

O prompt instrui a IA a analisar etiquetas de roupas:

```java
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
    """;
```

### Estrutura da requisição

```java
Map<String, Object> buildRequest(String base64Image, String mimeType) {
    // Parte da imagem (base64)
    Map<String, Object> inlineData = Map.of(
        "mimeType", mimeType,
        "data", base64Image
    );

    // Instruções do sistema
    Map<String, Object> systemInstruction = Map.of(
        "parts", List.of(Map.of("text", SYSTEM_INSTRUCTION))
    );

    return Map.of(
        "contents", List.of(content),
        "systemInstruction", systemInstruction
    );
}
```

### Parse da resposta

A IA retorna JSON dentro de uma estrutura aninhada:

```json
{
  "candidates": [{
    "content": {
      "parts": [{
        "text": "{\"descricao\":\"Camiseta\",\"tamanho\":\"M\",...}"
      }]
    }
  }]
}
```

O método `parseResponse` extrai e parseia o JSON interno.

## 🧪 Testando a API manualmente

### Teste com cURL

```bash
curl "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=SUA_CHAVE" \
  -H "Content-Type: application/json" \
  -d '{"contents":[{"parts":[{"text":"Hello"}]}]}'
```

### Resposta esperada

```json
{
  "candidates": [{
    "content": {
      "parts": [{
        "text": "Hello! How can I help you today?"
      }]
    }
  }]
}
```

## 📦 Exemplo de Resultado Real

Teste realizado em 12/02/2026 com imagem de etiqueta:

```
📦 Extracted Product Data:
   Descrição: KIT COM 3 CUECAS BOXER COMPRIDA
   Tamanho: M
   Cor: CARM/CROC/IND MAD
   Marca: DIAMANTES
   Código de Barras: 7912214020497
   Preço Venda: null
   Status IA: IA_SUGERIDO
   Status Validação: PENDENTE
```

**Tempo de resposta:** 7227 ms

## 🔒 Segurança

### ⚠️ IMPORTANTE: Proteção de Chaves de API

1. **Nunca commite chaves de API** no repositório
2. **Use arquivos `.env`** que são ignorados pelo Git
3. **Regenere chaves expostas** imediatamente
4. **Monitore o uso** no Google AI Studio

### Se uma chave for exposta:

1. Acesse [Google AI Studio](https://aistudio.google.com/)
2. Exclua a chave comprometida
3. Crie uma nova chave
4. Atualize o arquivo `.env`
