# Configuração do Ambiente de Desenvolvimento

Este documento descreve como configurar o ambiente de desenvolvimento do VisionStock.

## 📋 Pré-requisitos

- **Java 17** ou superior
- **Maven 3.8** ou superior
- **PostgreSQL 15** ou superior
- **VS Code** com extensões Java (recomendado)

## 🗄️ Configuração do Banco de Dados

### 1. Criar o banco de dados PostgreSQL

```sql
-- Conecte ao PostgreSQL como superusuário
psql -U postgres

-- Crie o banco de dados
CREATE DATABASE visionstock;

-- Verifique a criação
\l
```

### 2. Credenciais padrão utilizadas

| Parâmetro | Valor |
|-----------|-------|
| Host | localhost |
| Porta | 5432 |
| Banco | visionstock |
| Usuário | postgres |
| Senha | postgres |

## 🔐 Configuração de Variáveis de Ambiente

### Por que usar variáveis de ambiente?

Para **não expor credenciais sensíveis** no código-fonte, utilizamos um arquivo `.env` que é ignorado pelo Git.

### 1. Criar o arquivo `.env`

Na raiz do projeto backend (`visionstock/backend/`), crie o arquivo `.env`:

```properties
# Database Configuration
DB_URL=jdbc:postgresql://localhost:5432/visionstock
DB_USERNAME=postgres
DB_PASSWORD=postgres

# Google Gemini AI
GEMINI_API_KEY=sua-chave-api-aqui
```

### 2. Arquivo de exemplo (`.env.example`)

Este arquivo serve como template e **pode ser commitado** no repositório:

```properties
# Database Configuration
DB_URL=jdbc:postgresql://localhost:5432/visionstock
DB_USERNAME=seu_usuario
DB_PASSWORD=sua_senha

# Google Gemini AI
GEMINI_API_KEY=sua-chave-api-aqui
```

### 3. Adicionar `.env` ao `.gitignore`

```gitignore
# Environment variables - NUNCA commitar!
.env
.env.local
.env.*.local
```

## ⚙️ Arquivo `application.properties`

O arquivo `src/main/resources/application.properties` utiliza as variáveis de ambiente:

```properties
spring.application.name=visionstock-api

# PostgreSQL Configuration
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/visionstock}
spring.datasource.username=${DB_USERNAME:postgres}
spring.datasource.password=${DB_PASSWORD:}
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA / Hibernate
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
spring.jpa.open-in-view=false
spring.jpa.properties.hibernate.format_sql=true

# Default schema
spring.jpa.properties.hibernate.default_schema=public

# Google Gemini AI Configuration
gemini.api.key=${GEMINI_API_KEY:}
gemini.api.model=gemini-2.0-flash
gemini.api.url=https://generativelanguage.googleapis.com/v1beta/models

# File Upload Configuration
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

### Sintaxe de fallback

A sintaxe `${VARIAVEL:valor_padrao}` significa:
- Use o valor da variável de ambiente `VARIAVEL`
- Se não existir, use `valor_padrao`

## 🚀 Executando a Aplicação

### Método 1: Exportar variáveis e executar

```bash
cd visionstock/backend
export $(cat .env | xargs) && ./mvnw spring-boot:run
```

### Método 2: Usar variáveis inline

```bash
cd visionstock/backend
DB_PASSWORD=postgres GEMINI_API_KEY=sua-chave ./mvnw spring-boot:run
```

### Verificar se está funcionando

Após iniciar, você verá no console:

```
Started VisionStockApplication in 2.245 seconds
```

Acesse:
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **API Docs**: http://localhost:8080/v3/api-docs

## 🔧 Extensões VS Code Recomendadas

### Java
- Extension Pack for Java (`vscjava.vscode-java-pack`)
- **Lombok Annotations Support** (`vscjava.vscode-lombok`) - **ESSENCIAL**

### Spring Boot
- Spring Boot Extension Pack

### Problema: "package lombok does not exist"

Se você ver esse erro, instale a extensão Lombok:

1. Abra VS Code
2. Pressione `Ctrl+Shift+X`
3. Busque "Lombok"
4. Instale "Lombok Annotations Support for VS Code"
5. Recarregue a janela (`Ctrl+Shift+P` → "Reload Window")

## 📝 Notas Importantes

1. **Nunca commite o arquivo `.env`** - ele contém credenciais sensíveis
2. **Regenere chaves de API** se forem expostas acidentalmente
3. **Use o `.env.example`** como template para novos desenvolvedores
