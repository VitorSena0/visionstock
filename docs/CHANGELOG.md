# Changelog

Todas as mudanças notáveis deste projeto serão documentadas neste arquivo.

## [Unreleased] - 2026-02-14

### Adicionado

- **Testes completos do Approval Workflow**
  - `ValidationServiceTest.java` com 11 casos de teste unitários
  - Cobertura de 100% dos métodos públicos do `ValidationService`
  - Testes de criação de solicitações de validação
  - Testes de aprovação e rejeição de alterações
  - Testes de listagem e histórico de validações
  - Testes de contagem de solicitações pendentes
  - Testes de exceções e casos de erro (produto não encontrado, status inválido)
  
- **Documentação da atualização de testes**
  - `docs/TEST_SUITE_UPDATE.md` - documentação completa das mudanças realizadas
  - Métricas de cobertura de código e tempo de execução
  - Lições aprendidas sobre mocking vs. objetos reais
  - Checklist de verificação para futuros testes

- **Documentação Completa do Sistema**
  - `docs/COMPLETE_SYSTEM_OVERVIEW.md` - visão 360° do VisionStock
  - Arquitetura completa: banco de dados multi-schema, backend Spring Boot, integração com IA
  - Documentação de todas as tabelas (auth, inventory, finance, system)
  - Modelos de domínio: Product, ValidationRequest, StockMovement, User
  - Controllers e endpoints REST com exemplos de request/response
  - Services com lógica de negócio detalhada (ProductService, ValidationService, GeminiService)
  - DTOs e estratégias de proteção de dados sensíveis
  - Diagramas: arquitetura macro, ER, sequência do approval workflow
  - Fluxo completo da integração com Google Gemini 2.5 Flash
  - Cenários práticos de uso do approval workflow
  - Stack tecnológica completa

### Alterado

- **ProductControllerTest.java** corrigido
  - Adicionado `@MockBean` para `ValidationService`
  - Corrige erro de dependência não satisfeita no contexto do Spring
  - 5 testes do controller agora passam com sucesso

### Corrigido

- **Erro de dependência no ProductControllerTest**
  - Problema: `UnsatisfiedDependencyException` ao tentar instanciar `ProductController`
  - Causa: Faltava mock do `ValidationService` após refatoração do controller
  - Solução: Adicionado `@MockBean private ValidationService validationService`
  
- **ObjectMapper mockado causando NullPointerException**
  - Problema: Mock do `ObjectMapper` retornava `null` em `createObjectNode()`
  - Solução: Substituído mock por instância real do `ObjectMapper`
  - Justificativa: ObjectMapper é leve, rápido e não faz IO

### Métricas

- **Suite de testes completa**
  - ✅ 47 testes executados
  - ✅ 0 falhas
  - ✅ 0 erros
  - ⏭️ 2 testes de integração pulados (requerem API key)
  - ⏱️ Tempo total: ~5.3 segundos
  
- **Distribuição por módulo**
  - ValidationServiceTest: 11 testes (novo)
  - ProductServiceTest: 10 testes
  - ProductDTOTest: 9 testes
  - GeminiServiceTest: 7 testes
  - ProductControllerTest: 5 testes (corrigido)
  - ScanControllerTest: 3 testes
  - GeminiServiceIntegrationTest: 2 testes (opcional)

---

## [1.0.0] - 2026-02-13

### Adicionado

- **Fluxo de Aprovação (Approval Workflow)** para atualizações de produtos
  - Sistema de "Guard-Rail" diferenciando permissões por role (ADMIN vs USER/Estoquista)
  - `ValidationRequest` entidade JPA mapeada para tabela `inventory.validation_queue` com JSONB
  - `UserRole` enum (ADMIN, USER) para tipagem segura de papéis
  - `ValidationStatus` enum (PENDING, APPROVED, REJECTED) para estados de validação
  - `ProductUpdateDTO` com campos editáveis (descricao, cor, tamanho, precoVenda, nota)
  - `ValidationRequestDTO` para visualização em painel administrativo com diffs JSON
  - `ValidationRequestRepository` com 7 métodos de query especializados
  - `ValidationService` com lógica completa de aprovação/rejeição (7+ métodos)
  - `ProductService.updateProduct()` com Strategy Pattern baseado no role do usuário
  - 5 novos endpoints REST no `ProductController`:
    - `PUT /api/v1/products/{id}` - Atualizar com workflow (ADMIN = direto, USER = fila)
    - `GET /api/v1/validation` - Listar requisições pendentes (admin panel)
    - `POST /api/v1/validation/{id}/approve` - Aprovar mudanças
    - `POST /api/v1/validation/{id}/reject` - Rejeitar mudanças
    - `GET /api/v1/products/{id}/validations` - Histórico de validações do produto
  - `ResourceNotFoundException` exceção customizada
  - Operações transacionais (@Transactional) para atomicidade nas aprovações

- **Integração com Google Gemini AI** para análise de etiquetas de produtos
  - Endpoint `POST /api/scan` para upload de imagens
  - `GeminiService` para comunicação com a API Gemini
  - System instruction otimizado para extração de dados de vestuário

- **DTOs para diferentes níveis de acesso**
  - `ProductResponseDTO` - dados básicos para todos os usuários
  - `ProductAdminDTO` - dados completos incluindo informações financeiras

- **Testes automatizados**
  - `GeminiServiceTest` - testes unitários do parsing de resposta
  - `GeminiServiceIntegrationTest` - teste de integração com imagem real
  - `ScanControllerTest` - testes do controller
  - `ProductDTOTest` - testes dos DTOs

- **Documentação completa**
  - `docs/README.md` - índice da documentação
  - `docs/SETUP.md` - configuração do ambiente
  - `docs/GEMINI_INTEGRATION.md` - detalhes da integração com IA
  - `docs/TESTING.md` - guia de testes
  - `docs/TROUBLESHOOTING.md` - resolução de problemas
  - `docs/API.md` - documentação da API REST
  - `docs/APPROVAL_WORKFLOW.md` - documentação técnica completa do Approval Workflow (700+ linhas)
  - `docs/APPROVAL_WORKFLOW_TESTS.md` - guia com 6 cenários de teste práticos (350+ linhas)
  - `docs/IMPLEMENTATION_SUMMARY.md` - resumo executivo dos entregáveis
  - `docs/GIT_CONFLICT_RESOLUTION_TUTORIAL.md` - tutorial detalhado de resolução de conflitos de git

- **Configuração de variáveis de ambiente**
  - Arquivo `.env` para credenciais sensíveis
  - Arquivo `.env.example` como template

### Alterado

- **ProductService** refatorizado para suportar Strategy Pattern
  - `createProduct()` mantém comportamento original
  - Novo método `updateProduct()` que decide entre atualização direta ou criação de fila

- **ProductController** estendido com novos endpoints
  - Anterior: apenas `GET /api/v1/products` e `POST /api/v1/products`
  - Atual: 5 novos endpoints para gerenciar fluxo de aprovação

- **Modelo Gemini atualizado** de `gemini-1.5-flash` para `gemini-2.0-flash`
  - Modelo anterior foi descontinuado pelo Google

- **URL da API Gemini** corrigida de `v1` para `v1beta`
  - Versão `v1` não suporta todos os modelos

- **Tratamento de rate limiting** nos testes de integração
  - Testes são automaticamente pulados (skipped) quando há erro 429
  - Usa `assumeTrue()` ao invés de falhar o teste

### Corrigido

- **Conflitos de git durante merge de branches divergentes**
  - Cenário: rebase travado com arquivo swap do editor
  - Solução: transição para merge strategy com resolução manual de conflitos
  - Resultado: sincronização bem-sucedida de Approval Workflow com origin/develop

- **Arquivo COMMIT_EDITMSG.swp** travando rebase
  - Causa: vim/vi deixou sessão de edição aberta
  - Solução: limpeza de arquivos swap e reset de estado git

- Erro `400 Bad Request` ao chamar API Gemini
  - Causa: URL incorreta (`v1` ao invés de `v1beta`)

- Erro `404 Not Found` para modelo `gemini-1.5-flash`
  - Causa: Modelo descontinuado

- Erro de compilação "package lombok does not exist"
  - Solução: Documentação de instalação da extensão Lombok

### Segurança

- ⚠️ **Chave de API exposta** durante desenvolvimento
  - Recomendação: Regenerar chave após testes
  - Implementado: Uso de variáveis de ambiente

---

## Convenções

Este changelog segue as convenções do [Keep a Changelog](https://keepachangelog.com/pt-BR/1.0.0/).

### Categorias de mudanças

- **Adicionado** - novas funcionalidades
- **Alterado** - mudanças em funcionalidades existentes
- **Obsoleto** - funcionalidades que serão removidas
- **Removido** - funcionalidades removidas
- **Corrigido** - correções de bugs
- **Segurança** - correções de vulnerabilidades
