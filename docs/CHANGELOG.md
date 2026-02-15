# Changelog

Todas as mudanças notáveis deste projeto serão documentadas neste arquivo.

## [Unreleased] - 2026-02-15

### Adicionado

- **Etapa Mobile 1 - Base React Native (Expo) Offline-First**
  - Estrutura inicial do app em `mobile/vision-stock-mobile` com:
    - `Expo Router` (rotas por arquivo)
    - `TypeScript` com tipagem estrita
    - `Axios` com interceptors de autenticação
    - `Zustand` para sessão (`token`, `user`, `login`, `logout`, `hydrate`)
    - `@tanstack/react-query` no layout raiz
    - `expo-secure-store` para persistir JWT
    - `expo-sqlite` com bootstrap inicial da base local e tabela `sync_queue`
  - Tela real de login integrada ao backend (`POST /api/v1/auth/login`)
  - Home inicial autenticada com logout
  - Componentes UI base (`AppButton`, `AppInput`) e design system com NativeWind
  - Arquivos de configuração mobile adicionados:
    - `babel.config.js`, `tailwind.config.js`, `metro.config.js`, `global.css`, `.env.example`

- **Observabilidade de conexão no app mobile**
  - Exibição da URL de API ativa na tela de login (`API: ...`)
  - Indicador quando `EXPO_PUBLIC_API_URL` não foi carregada do `.env`
  - Mensagens de erro de login diferenciando:
    - credencial inválida (`401`)
    - falha de conectividade com API (timeout/rede)
    - outros erros HTTP

- **Etapa 6 - Segurança e Autenticação com JWT (Spring Security 6)**
  - Dependências de segurança adicionadas ao backend:
    - `spring-boot-starter-security`
    - `jjwt-api`, `jjwt-impl`, `jjwt-jackson`
    - `spring-security-test`
  - Nova camada `security/` com componentes:
    - `SecurityConfig` com `SecurityFilterChain` stateless
    - `JwtService` para gerar/validar token e extrair claims
    - `JwtAuthenticationFilter` aplicado antes de `UsernamePasswordAuthenticationFilter`
    - `CustomUserDetailsService` e principal customizado `AuthenticatedUser`
    - Handlers REST para erros de autenticação/autorização (`401`/`403`)
  - Autenticação implementada:
    - `AuthService` com `register` e `login`
    - Senhas com hash `BCryptPasswordEncoder`
    - JWT com claims `userId` e `role`
  - Novos endpoints públicos de autenticação:
    - `POST /api/v1/auth/register`
    - `POST /api/v1/auth/login`
  - Novo `ValidationController` dedicado:
    - `GET /api/v1/validation`
    - `POST /api/v1/validation/{id}/approve`
    - `POST /api/v1/validation/{id}/reject`
    - Compatibilidade mantida para `/api/v1/products/validation/**`
  - Novos artefatos de domínio de autenticação:
    - `UserRepository`
    - `LoginDTO`, `RegisterDTO`, `AuthResponseDTO`, `ValidationDecisionDTO`
    - `ApiErrorResponse` e `GlobalExceptionHandler`

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

- **Novo tutorial end-to-end de setup**
  - `docs/FULL_STACK_SETUP_TUTORIAL.md`
  - Passo a passo completo para banco, backend e mobile (React Native Expo)
  - Inclui validação com `curl` e troubleshooting de rede/JWT/estilização
  - Template de ambiente backend adicionado em `backend/.env.example`

### Alterado

- **Backend configurado para acesso em rede local**
  - `server.address=${SERVER_ADDRESS:0.0.0.0}`
  - `server.port=${PORT:8080}`
  - Permite acesso do app mobile físico na mesma rede (LAN)

- **Normalização da URL de API no app mobile**
  - Base URL agora remove barra final e sufixo `/api/v1` quando necessário
  - Prefixo `/api/v1` aplicado de forma centralizada (`withApiPrefix`)
  - Evita erro de rota duplicada (`/api/v1/api/v1/...`)

- **Documentação principal atualizada para estado real do projeto**
  - `docs/COMPLETE_SYSTEM_OVERVIEW.md` passou a refletir frontend atual em React Native (Expo)
  - Arquitetura macro e stack tecnológica revisadas

- **ProductController atualizado para identidade via JWT**
  - Removidos parâmetros manuais de identificação do usuário em update/create
  - `@AuthenticationPrincipal` agora fornece o usuário autenticado
  - `createProduct` passa a preencher `createdBy`/`updatedBy` com o `userId` do token
  - `updateProduct` passa a usar `role` e `userId` vindos do contexto de segurança

- **ProductService.createProduct()**
  - Assinatura alterada para receber `createdBy` explicitamente a partir do contexto autenticado
  - Movimentação inicial de estoque vinculada ao usuário autenticado

- **Controle de acesso por endpoint aplicado em `SecurityConfig`**
  - `/api/v1/auth/**` público
  - `/api/v1/scan` restrito a `USER`/`ADMIN`
  - `/api/v1/validation/**` restrito a `ADMIN`
  - `POST`/`PUT` em `/api/v1/products` restritos a `USER`/`ADMIN`

- **ProductControllerTest.java** corrigido
  - Adicionado `@MockBean` para `ValidationService`
  - Corrige erro de dependência não satisfeita no contexto do Spring
  - 5 testes do controller agora passam com sucesso

### Corrigido

- **Tratamento de erro HTTP para métodos/requests inválidos no backend**
  - `GET /api/v1/auth/login` não é mais reportado como erro genérico interno
  - `GlobalExceptionHandler` agora mapeia:
    - `HttpRequestMethodNotSupportedException` → `405 Method Not Allowed`
    - `HttpMessageNotReadableException` → `400 Bad Request`

- **Configuração NativeWind corrigida no app mobile**
  - Ajuste de `babel + metro + tailwind + global.css`
  - Corrige renderização “sem estilo” (UI estática sem classes aplicadas)

- **Respostas de erro de autenticação/autorização em JSON limpo**
  - `401 Unauthorized` agora retorna payload padronizado (sem stacktrace padrão do Spring)
  - `403 Forbidden` agora retorna payload padronizado

- **Ambiente de testes com Mockito no JDK 21**
  - Adicionado `mock-maker-subclass` em `src/test/resources/mockito-extensions`
  - Evita falha de attach do agente inline em ambientes restritos

- **Erro de dependência no ProductControllerTest**
  - Problema: `UnsatisfiedDependencyException` ao tentar instanciar `ProductController`
  - Causa: Faltava mock do `ValidationService` após refatoração do controller
  - Solução: Adicionado `@MockBean private ValidationService validationService`
  
- **ObjectMapper mockado causando NullPointerException**
  - Problema: Mock do `ObjectMapper` retornava `null` em `createObjectNode()`
  - Solução: Substituído mock por instância real do `ObjectMapper`
  - Justificativa: ObjectMapper é leve, rápido e não faz IO

### Métricas

- **Validação mobile (Etapa 1)**
  - ✅ `npx tsc --noEmit` executado com sucesso em `mobile/vision-stock-mobile`
  - ✅ `npx expo export --platform android --clear` executado com sucesso

- **Validação pós-Etapa 6**
  - ✅ `mvn -q -DskipTests compile` executado com sucesso
  - ✅ `mvn -q test` executado com sucesso após integração JWT

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

- **Autenticação e autorização JWT implementadas**
  - API passou a operar em modo stateless para endpoints protegidos
  - Roles `ADMIN` e `USER` aplicadas no nível de filtro de segurança
  - Identidade do usuário deixa de depender de parâmetros manipuláveis no request

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
