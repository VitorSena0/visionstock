# Troubleshooting - Resolução de Problemas

Este documento lista os problemas comuns encontrados durante o desenvolvimento e suas soluções.

## 🔴 Erros de Compilação

### 1. "package lombok does not exist"

**Sintoma:**
```
error: package lombok does not exist
import lombok.Data;
```

**Causa:** Extensão Lombok não instalada no VS Code.

**Solução:**

1. Instale a extensão Lombok no VS Code:
   - Abra VS Code
   - Pressione `Ctrl+Shift+X`
   - Busque "Lombok"
   - Instale "Lombok Annotations Support for VS Code"

2. Recarregue a janela do VS Code:
   - Pressione `Ctrl+Shift+P`
   - Digite "Reload Window"
   - Pressione Enter

3. Limpe o cache do projeto Java:
   - Pressione `Ctrl+Shift+P`
   - Digite "Java: Clean Java Language Server Workspace"

---

## 🔴 Erros de Banco de Dados

### 2. "Connection refused" ao conectar no PostgreSQL

**Sintoma:**
```
Connection refused to host: localhost, port: 5432
```

**Causas possíveis:**
- PostgreSQL não está rodando
- Porta incorreta
- Credenciais erradas

**Solução:**

1. Verifique se o PostgreSQL está rodando:
```bash
sudo systemctl status postgresql
```

2. Inicie se necessário:
```bash
sudo systemctl start postgresql
```

3. Verifique as credenciais no `.env`:
```properties
DB_URL=jdbc:postgresql://localhost:5432/visionstock
DB_USERNAME=postgres
DB_PASSWORD=postgres
```

### 3. "database 'visionstock' does not exist"

**Solução:**
```sql
-- Conecte ao PostgreSQL
psql -U postgres

-- Crie o banco
CREATE DATABASE visionstock;
```

---

## 🔴 Erros da API Gemini

### 4. Erro 404 - "models/gemini-1.5-flash is not found"

**Sintoma:**
```json
{
  "error": {
    "code": 404,
    "message": "models/gemini-1.5-flash is not found for API version v1beta"
  }
}
```

**Causa:** O modelo `gemini-1.5-flash` foi descontinuado.

**Solução:**

Atualize o modelo em `application.properties`:
```properties
# ANTES (não funciona mais)
gemini.api.model=gemini-1.5-flash

# DEPOIS (correto)
gemini.api.model=gemini-2.0-flash
```

### 5. Erro 400 - "Bad Request"

**Sintoma:**
```
400 Bad Request from POST https://generativelanguage.googleapis.com/v1/models/...
```

**Causa:** URL da API incorreta (usando `v1` ao invés de `v1beta`).

**Solução:**

Verifique a URL base em `application.properties`:
```properties
# ERRADO
gemini.api.url=https://generativelanguage.googleapis.com/v1/models

# CORRETO
gemini.api.url=https://generativelanguage.googleapis.com/v1beta/models
```

### 6. Erro 429 - "Too Many Requests" / "Quota Exceeded"

**Sintoma:**
```json
{
  "error": {
    "code": 429,
    "message": "You exceeded your current quota",
    "status": "RESOURCE_EXHAUSTED"
  }
}
```

**Causa:** Limite de requisições do plano gratuito excedido.

**Limites do plano gratuito:**
| Métrica | Limite |
|---------|--------|
| Requisições/minuto | 15 |
| Requisições/dia | 1.500 |
| Tokens/minuto | 1.000.000 |

**Solução:**

1. Aguarde o tempo indicado na resposta (~60 segundos)
2. Monitore seu uso em: https://ai.dev/rate-limit
3. Considere upgrade para plano pago se necessário

### 7. Erro 401/403 - "Invalid API Key"

**Sintoma:**
```json
{
  "error": {
    "code": 403,
    "message": "API key not valid"
  }
}
```

**Causas possíveis:**
- Chave de API inválida ou expirada
- Chave não tem permissão para o modelo
- Variável de ambiente não carregada

**Solução:**

1. Verifique se a chave está configurada:
```bash
echo $GEMINI_API_KEY
```

2. Teste a chave diretamente:
```bash
curl "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=SUA_CHAVE" \
  -H "Content-Type: application/json" \
  -d '{"contents":[{"parts":[{"text":"Hello"}]}]}'
```

3. Gere uma nova chave em: https://aistudio.google.com/

---

## 🔴 Erros de Testes

### 8. Teste de integração falha com "ERRO_IA"

**Sintoma:**
```
AssertionFailedError: Should not return error status for valid image
expected: not equal but was: <ERRO_IA>
```

**Causas possíveis:**
- Rate limiting (429)
- API key não configurada
- Imagem não encontrada

**Solução:**

1. Verifique se a imagem existe:
```bash
ls src/test/resources/image.jpg
```

2. Verifique a variável de ambiente:
```bash
echo $GEMINI_API_KEY
```

3. Aguarde 60 segundos (rate limiting) e execute novamente:
```bash
GEMINI_API_KEY=sua-chave ./mvnw test -Dtest=GeminiServiceIntegrationTest
```

### 9. Teste skipped - "image.jpg not found"

**Sintoma:**
```
⚠️ Skipping test: image.jpg not found in src/test/resources/
```

**Solução:**

Adicione uma imagem de etiqueta de roupa em:
```
src/test/resources/image.jpg
```

---

## 🔴 Erros de Spring Boot

### 10. Warning: "spring.jpa.open-in-view is enabled by default"

**Sintoma:**
```
WARN: spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering.
```

**Solução:**

Adicione em `application.properties`:
```properties
spring.jpa.open-in-view=false
```

### 11. Warning: "PostgreSQLDialect does not need to be specified explicitly"

**Sintoma:**
```
WARN: HHH90000025: PostgreSQLDialect does not need to be specified explicitly
```

**Solução:**

Remova a linha do `application.properties`:
```properties
# REMOVER esta linha (Hibernate detecta automaticamente)
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
```

---

## 📋 Checklist de Diagnóstico

Quando algo não funcionar, verifique:

### Ambiente
- [ ] Java 17+ instalado (`java -version`)
- [ ] Maven instalado (`mvn -version`)
- [ ] PostgreSQL rodando (`systemctl status postgresql`)
- [ ] Extensão Lombok instalada no VS Code

### Configuração
- [ ] Arquivo `.env` existe e tem valores corretos
- [ ] Variáveis de ambiente estão sendo carregadas
- [ ] `application.properties` usando variáveis corretamente

### API Gemini
- [ ] Chave de API válida
- [ ] Modelo correto (`gemini-2.0-flash`)
- [ ] URL correta (`v1beta`)
- [ ] Cota não excedida

### Testes
- [ ] Imagem de teste existe em `src/test/resources/`
- [ ] `GEMINI_API_KEY` exportada antes de executar testes

---

## 🆘 Ainda com problemas?

1. Verifique os logs completos:
```bash
./mvnw spring-boot:run -X
```

2. Limpe e recompile:
```bash
./mvnw clean compile
```

3. Verifique relatórios de teste:
```bash
cat target/surefire-reports/*.txt
```
