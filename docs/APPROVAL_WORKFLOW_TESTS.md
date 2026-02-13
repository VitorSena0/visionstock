# Exemplos de Teste - Approval Workflow

## 🔧 Setup Inicial

### 1. Variáveis de Exemplo
```bash
# IDs de exemplo (use UUIDs reais em produção)
PRODUCT_ID="f47ac10b-58cc-4372-a567-0e02b2c3d479"
ESTOQUISTA_ID="a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"
ADMIN_ID="b0ffb0e9-9c0b-4ef8-bb6d-6bb9bd380a12"
VALIDATION_ID="c1ffc10c-9c0b-4ef8-bb6d-6bb9bd380a13"
BASE_URL="http://localhost:8080/api/v1"
```

---

## 📝 Teste 1: Criar um Produto (Baseline)

### Request
```bash
curl -X POST "$BASE_URL/products" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "'"$PRODUCT_ID"'",
    "referencia": "CAM-POLO-001",
    "descricao": "Camiseta Polo Azul",
    "tamanho": "M",
    "cor": "Azul",
    "marca": "Nike",
    "codigoBarras": "7891234567890",
    "precoCusto": 45.00,
    "precoVenda": 89.90,
    "quantidadeInicial": 50
  }'
```

### Response (201 Created)
```json
{
  "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "referencia": "CAM-POLO-001",
  "descricao": "Camiseta Polo Azul",
  "cor": "Azul",
  "tamanho": "M",
  "precoVenda": "89.90",
  "quantidadeAtual": 50,
  "statusIa": "VERIFICADO",
  "statusValidacao": "OK",
  "createdAt": "2026-02-13T20:30:00Z"
}
```

✅ **Produto criado com sucesso!**

---

## 🔄 Teste 2: Estoquista Solicita Alteração

### Request (USER solicita mudança)
```bash
curl -X PUT \
  "$BASE_URL/products/$PRODUCT_ID?role=USER&userId=$ESTOQUISTA_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "descricao": "Camiseta Polo Azul Marinho",
    "cor": "Azul Marinho",
    "precoVenda": 99.90,
    "nota": "Cor mais escura, preço competitivo"
  }'
```

### Response (202 Accepted)
```json
{
  "success": true,
  "status": "PENDING_APPROVAL",
  "message": "Alteração enviada para aprovação do gerente",
  "validationRequestId": "c1ffc10c-9c0b-4ef8-bb6d-6bb9bd380a13",
  "product": {
    "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "referencia": "CAM-POLO-001",
    "descricao": "Camiseta Polo Azul",
    "cor": "Azul",
    "precoVenda": "89.90"
  }
}
```

⚠️ **Importante:**
- HTTP 202 (Accepted, não 200)
- `status` = `PENDING_APPROVAL` (não `UPDATED`)
- `product` ainda tem dados ANTIGOS
- `validationRequestId` = `c1ffc10c-9c0b-4ef8-bb6d-6bb9bd380a13`

---

## ✅ Teste 3: Admin Verifica Fila de Validação

### Request
```bash
curl "$BASE_URL/validation" \
  -H "Content-Type: application/json"
```

### Response (200 OK)
```json
[
  {
    "id": "c1ffc10c-9c0b-4ef8-bb6d-6bb9bd380a13",
    "productId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "productReferencia": "CAM-POLO-001",
    "productDescricao": "Camiseta Polo Azul",
    "requestedBy": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
    "requestedByName": "João Silva (Estoquista)",
    "status": "PENDING",
    "originalData": {
      "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
      "descricao": "Camiseta Polo Azul",
      "cor": "Azul",
      "tamanho": "M",
      "precoVenda": "89.90"
    },
    "newData": {
      "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
      "descricao": "Camiseta Polo Azul Marinho",
      "cor": "Azul Marinho",
      "tamanho": "M",
      "precoVenda": "99.90"
    },
    "requestedAt": "2026-02-13T20:30:15Z",
    "changesSummary": "Descrição alterada, Cor alterada, Preço alterado"
  }
]
```

📋 **Análise para o Admin:**
- 1 alteração pendente
- João Silva solicitou mudança na camiseta polo
- **O que mudou:**
  - ~~Camiseta Polo Azul~~ → **Camiseta Polo Azul Marinho**
  - ~~Azul~~ → **Azul Marinho**
  - ~~89.90~~ → **99.90**

---

## ✔️ Teste 4: Admin APROVA a Alteração

### Request
```bash
curl -X POST \
  "$BASE_URL/validation/c1ffc10c-9c0b-4ef8-bb6d-6bb9bd380a13/approve?adminId=$ADMIN_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "reviewNote": "Cor e preço conferidos. Aprovado!"
  }'
```

### Response (200 OK)
```json
{
  "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "referencia": "CAM-POLO-001",
  "descricao": "Camiseta Polo Azul Marinho",
  "cor": "Azul Marinho",
  "tamanho": "M",
  "precoVenda": "99.90",
  "quantidadeAtual": 50,
  "statusIa": "VERIFICADO",
  "updatedAt": "2026-02-13T20:32:00Z",
  "updatedBy": "b0ffb0e9-9c0b-4ef8-bb6d-6bb9bd380a12"
}
```

✅ **Alteração APROVADA e APLICADA!**
- Descrição: Camiseta Polo Azul → **Camiseta Polo Azul Marinho**
- Cor: Azul → **Azul Marinho**
- Preço: 89.90 → **99.90**
- ValidationRequest.status = **APPROVED**

---

## ❌ Teste 4b: Admin REJEITA a Alteração (Alternativa)

(Se tivéssemos feito rejeição em vez de aprovação)

### Request
```bash
curl -X POST \
  "$BASE_URL/validation/c1ffc10c-9c0b-4ef8-bb6d-6bb9bd380a13/reject?adminId=$ADMIN_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "reviewNote": "Preço já está competitivo em 89.90. Verificaremos cor com o fornecedor."
  }'
```

### Response (200 OK)
```json
{
  "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "referencia": "CAM-POLO-001",
  "descricao": "Camiseta Polo Azul",
  "cor": "Azul",
  "tamanho": "M",
  "precoVenda": "89.90",
  "updatedAt": "2026-02-13T20:30:15Z"
}
```

❌ **Alteração REJEITADA!**
- Produto mantém valores ORIGINAIS
- ValidationRequest.status = **REJECTED**
- Estoquista saberá que foi recusado

---

## 🔍 Teste 5: Consultar Histórico de Validações do Produto

### Request
```bash
curl "$BASE_URL/products/$PRODUCT_ID/validations"
```

### Response (200 OK)
```json
[
  {
    "id": "c1ffc10c-9c0b-4ef8-bb6d-6bb9bd380a13",
    "productId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "status": "APPROVED",
    "requestedAt": "2026-02-13T20:30:15Z",
    "reviewedAt": "2026-02-13T20:32:00Z",
    "reviewedBy": "b0ffb0e9-9c0b-4ef8-bb6d-6bb9bd380a12",
    "reviewNote": "Cor e preço conferidos. Aprovado!",
    "changesSummary": "Descrição alterada, Cor alterada, Preço alterado"
  }
]
```

📊 **Auditoria completa do produto**

---

## 👑 Teste 6: Admin Atualiza Direto (Sem Fila)

### Request (ADMIN bypassa a fila)
```bash
curl -X PUT \
  "$BASE_URL/products/$PRODUCT_ID?role=ADMIN&userId=$ADMIN_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "descricao": "Camiseta Polo Premium Azul Marinho",
    "precoVenda": 129.90
  }'
```

### Response (200 OK)
```json
{
  "success": true,
  "status": "UPDATED",
  "message": "Produto atualizado com sucesso",
  "product": {
    "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "referencia": "CAM-POLO-001",
    "descricao": "Camiseta Polo Premium Azul Marinho",
    "precoVenda": "129.90"
  }
}
```

✅ **ATUALIZADO IMEDIATAMENTE!**
- HTTP 200 (sucesso imediato)
- `status` = `UPDATED` (não PENDING_APPROVAL)
- **SEM** criar ValidationRequest
- Admin pode mudar diretamente sem fila

---

## 🧪 Teste Postman (Collection)

```json
{
  "info": {
    "name": "VisionStock Approval Workflow",
    "description": "Testes do fluxo de aprovação de produtos",
    "version": "1.0"
  },
  "item": [
    {
      "name": "1. Criar Produto",
      "request": {
        "method": "POST",
        "header": [
          {"key": "Content-Type", "value": "application/json"}
        ],
        "url": {"raw": "{{BASE_URL}}/products", "host": ["{{BASE_URL}}"], "path": ["products"]},
        "body": {
          "mode": "raw",
          "raw": "{\"id\": \"{{PRODUCT_ID}}\", \"referencia\": \"CAM-POLO-001\", \"descricao\": \"Camiseta Polo Azul\", \"precoVenda\": 89.90, \"quantidadeInicial\": 50}"
        }
      }
    },
    {
      "name": "2. Estoquista Solicita Alteração",
      "request": {
        "method": "PUT",
        "url": {"raw": "{{BASE_URL}}/products/{{PRODUCT_ID}}?role=USER&userId={{ESTOQUISTA_ID}}", "query": [{"key": "role", "value": "USER"}, {"key": "userId", "value": "{{ESTOQUISTA_ID}}"}]},
        "body": {
          "mode": "raw",
          "raw": "{\"descricao\": \"Camiseta Polo Azul Marinho\", \"precoVenda\": 99.90}"
        }
      }
    },
    {
      "name": "3. Admin Aprova",
      "request": {
        "method": "POST",
        "url": {"raw": "{{BASE_URL}}/validation/{{VALIDATION_ID}}/approve?adminId={{ADMIN_ID}}", "query": [{"key": "adminId", "value": "{{ADMIN_ID}}"}]},
        "body": {"mode": "raw", "raw": "{\"reviewNote\": \"Aprovado\"}"}
      }
    }
  ],
  "variable": [
    {"key": "BASE_URL", "value": "http://localhost:8080/api/v1"},
    {"key": "PRODUCT_ID", "value": "f47ac10b-58cc-4372-a567-0e02b2c3d479"},
    {"key": "ESTOQUISTA_ID", "value": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"},
    {"key": "ADMIN_ID", "value": "b0ffb0e9-9c0b-4ef8-bb6d-6bb9bd380a12"},
    {"key": "VALIDATION_ID", "value": "c1ffc10c-9c0b-4ef8-bb6d-6bb9bd380a13"}
  ]
}
```

---

## 📊 Matriz de Testes

| Teste | Caso | Esperado | Resultado |
|-------|------|----------|-----------|
| 1 | Criar produto | HTTP 201, produto criado | ✅ |
| 2 | USER atualiza | HTTP 202, PENDING_APPROVAL | ✅ |
| 3 | Admin lista | Mostra 1 pendente | ✅ |
| 4 | Admin aprova | HTTP 200, APPROVED, dados atualizados | ✅ |
| 4b | Admin rejeita | HTTP 200, REJECTED, dados inalterados | ✅ |
| 5 | Histórico | Mostra aprovações/rejeições | ✅ |
| 6 | Admin direto | HTTP 200, UPDATED, sem fila | ✅ |

---

## 🐛 Troubleshooting

### "Product not found"
```json
{
  "error": "Product not found with ID: invalid-uuid"
}
```
**Solução:** Use `PRODUCT_ID` correto do passo 1

### "Validation request not found"
```json
{
  "error": "Validation request not found with ID: invalid-uuid"
}
```
**Solução:** Use `VALIDATION_ID` retornado no passo 2

### "Cannot approve request with status: REJECTED"
```json
{
  "error": "Cannot approve request with status: REJECTED"
}
```
**Solução:** Já foi rejeitado anteriormente

---

**Testes completos e prontos! 🚀**
