# VisionStock - Documentação

Documentação completa do projeto VisionStock, um sistema de gestão de estoque com integração de IA para análise de etiquetas de produtos.

## 📁 Estrutura da Documentação

| Documento | Descrição |
|-----------|-----------|
| [SETUP.md](./SETUP.md) | Configuração do ambiente de desenvolvimento |
| [GEMINI_INTEGRATION.md](./GEMINI_INTEGRATION.md) | Integração com Google Gemini AI |
| [TESTING.md](./TESTING.md) | Guia de testes unitários e de integração |
| [TEST_SUITE_UPDATE.md](./TEST_SUITE_UPDATE.md) | Atualização da suite de testes (fevereiro 2026) |
| [TROUBLESHOOTING.md](./TROUBLESHOOTING.md) | Resolução de problemas comuns |
| [API.md](./API.md) | Documentação da API REST |
| [APPROVAL_WORKFLOW.md](./APPROVAL_WORKFLOW.md) | Documentação técnica do Approval Workflow |
| [APPROVAL_WORKFLOW_TESTS.md](./APPROVAL_WORKFLOW_TESTS.md) | Guia de testes do Approval Workflow |
| [GIT_CONFLICT_RESOLUTION_TUTORIAL.md](./GIT_CONFLICT_RESOLUTION_TUTORIAL.md) | Tutorial de resolução de conflitos Git |
| [CHANGELOG.md](./CHANGELOG.md) | Histórico de mudanças do projeto |

## 🚀 Início Rápido

```bash
# 1. Clone o repositório
git clone https://github.com/VitorSena0/visionstock.git
cd visionstock/backend

# 2. Configure o arquivo .env
cp .env.example .env
# Edite o .env com suas credenciais

# 3. Execute a aplicação
./mvnw spring-boot:run

# 4. Acesse o Swagger UI
open http://localhost:8080/swagger-ui.html
```

## 📋 Requisitos

- Java 17+
- Maven 3.8+
- PostgreSQL 15+
- Chave de API do Google Gemini

## 🏗️ Arquitetura

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Frontend      │────▶│   Backend       │────▶│   PostgreSQL    │
│   (Flutter)     │     │   (Spring Boot) │     │                 │
└─────────────────┘     └────────┬────────┘     └─────────────────┘
                                 │
                                 ▼
                        ┌─────────────────┐
                        │   Google        │
                        │   Gemini AI     │
                        └─────────────────┘
```

## 📅 Data de Criação

12 de Fevereiro de 2026
