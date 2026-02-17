# Changelog

Todas as mudanças notáveis deste projeto serão documentadas neste arquivo.

## [Unreleased] - 2026-02-17

### Adicionado

- **Etapa 8.2.3 - Estabilização de cadastro/imagens em rede instável**
  - **Backend**
    - Novo tratamento explícito de indisponibilidade da IA:
      - `ExternalServiceRateLimitException` para `429 Too Many Requests`
      - `ExternalServiceException` para falhas de integração (`502`)
    - `GeminiService` com retry/backoff para chamadas ao Gemini:
      - até 3 tentativas para `429` e respostas `5xx`
      - leitura de `Retry-After` quando disponível
    - `GlobalExceptionHandler` atualizado para erros externos:
      - `429` com mensagem amigável e header `Retry-After` (quando presente)
      - `502` em falhas de gateway externo
    - Tratamento de integridade de dados endurecido no `GlobalExceptionHandler`:
      - `409` para duplicidade de `referencia`/`codigoBarras`
      - `409` para conflito de imagem principal (`ux_product_images_primary_per_product`)
      - `400` para overflow numérico (`SQLState 22003`)
    - Upload de imagem com deduplicação por hash (`sha256`) para reduzir duplicação em retry/timeout
    - Nova migration `database/migrations/003_harden_markup_and_conflicts.sql`:
      - ajuste de `inventory.products.markup_percentual` para `DECIMAL(10,2)`
  - **Mobile**
    - Tratamento dedicado de erro `429` no fluxo de scan com orientação de espera
    - Normalização determinística de upload de imagem via `expo-image-manipulator`
    - Fluxo de criação com `draftProductId` estável para reduzir duplicações em retries
    - Cache local autenticado de imagens remotas via `expo-file-system` + `axios` (`imageContentService`)
    - Colunas de cache em `product_images` local (`cached_uri`, `cache_status`, `cache_updated_at`, `cache_error`)
    - Reconciliação de conflito de cadastro: recuperação do produto existente + opção de ajuste de estoque

- **Etapa 8.1/8.2 - Busca instantânea, edição completa e gestão estruturada de imagens**
  - **Backend**
    - Novos endpoints em `ProductController`:
      - `POST /api/v1/products/{id}/stock-adjustments`
      - `GET /api/v1/products/{id}/images`
      - `GET /api/v1/products/{id}/images/{imageId}/content`
      - `POST /api/v1/products/{id}/images` (multipart)
      - `PATCH /api/v1/products/{id}/images/{imageId}/primary`
      - `DELETE /api/v1/products/{id}/images/{imageId}`
    - Novo endpoint de reconciliação por usuário:
      - `GET /api/v1/validation/my`
    - Novos modelos/DTOs/repos para mídia e validação por tipo de mudança:
      - `ProductImage`, `ValidationImageStaging`
      - `ActionResponseDTO`, `StockAdjustmentDTO`, `ProductImageMetadataDTO`
      - `ValidationChangeType`, `ImageOperationType`
  - **Banco de dados**
    - Nova migration `database/migrations/002_add_product_images_and_validation_change_type.sql` com:
      - tabela `inventory.product_images` (blob em `bytea`)
      - tabela `inventory.validation_image_staging`
      - coluna `change_type` em `inventory.validation_queue`
      - índices e constraint de imagem principal por produto
  - **Mobile**
    - Nova rota de detalhe/edição:
      - `app/product/[id]/index.tsx` (detalhe com galeria)
      - `app/product/[id]/edit.tsx` (edição completa)
    - Gestão de imagem no app:
      - anexar por câmera/galeria no detalhe
      - definir principal
      - remover imagem
      - auto-anexo da foto no cadastro (scan/manual) após salvar produto
    - Fila offline expandida em SQLite:
      - operações `PRODUCT_UPDATE`, `STOCK_ADJUSTMENT`, `IMAGE_ADD`, `IMAGE_DELETE`, `IMAGE_SET_PRIMARY`
      - novas tabelas locais `product_images` e `pending_edits`
    - Sincronização expandida:
      - `pushPendingChanges()` antes do pull
      - reconciliação com `GET /validation/my`
      - invalidação de queries para lista, detalhe e imagens

- **Etapa 7 - Fluxo Principal (Scan + Cadastro) no mobile**
  - Integração completa de câmera e galeria com `expo-image-picker`:
    - Botão de scan com ações separadas para câmera e galeria (`ImagePickerButton`)
    - Permissões de câmera/galeria configuradas em `app.json`
  - Integração de upload de imagem com backend via `multipart/form-data`:
    - `uploadImage(imageUri)` em `src/services/api.ts`
    - Montagem de `FormData` no formato compatível com React Native (`uri`, `name`, `type`)
  - Fluxo de criação de produto com revisão antes de salvar:
    - Modal de rascunho com mensagem explícita de que nada foi salvo ainda
    - Submit do formulário para `POST /api/v1/products` com `useMutation`
  - Cadastro manual disponível sem depender de sucesso da IA:
    - Botão "Cadastrar manualmente" na home
    - Ação de fallback no alerta de erro do scan
  - Novo formulário de produto com `react-hook-form + zod` (`ProductForm`):
    - Campos: `referencia`, `codigoBarras`, `descricao`, `precoCusto`, `precoVenda`,
      `quantidadeInicial`, `quantidadeMinima`, `tamanho`, `cor`, `marca`
    - Validação de decimal para preços e inteiro não-negativo para quantidades
  - Tipos de produto no mobile adicionados em `src/types/product.ts`

- **Bootstrap opcional de ADMIN em ambiente local/dev**
  - Novo `DataInitializer` (`CommandLineRunner`) em `backend/src/main/java/com/visionstock/config/DataInitializer.java`
  - Ativação controlada por perfil (`dev`/`local`) e flag:
    - `SEED_ADMIN_ENABLED`
    - `SEED_ADMIN_NOME`
    - `SEED_ADMIN_EMAIL`
    - `SEED_ADMIN_PASSWORD`
  - Propriedades adicionadas em `application.properties` e `backend/.env.example`

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

- **Confiabilidade de imagem principal (backend)**
  - `ProductImageService` passou a usar lock por produto + atualização atômica para primária
  - `ProductImageRepository.clearPrimaryByProductId()` agora limpa todas as imagens ativas do produto
  - Fluxos de upload/troca/remoção de imagem principal deixaram de depender de save em loop
  - Endpoint de conteúdo de imagem passou a responder com `Content-Length` baseado em `image_data.length`

- **Reconciliação de imagem no mobile (server wins com exceção pendente explícita)**
  - `productImageRepository.upsertRemoteMetadata()` preserva primária local apenas quando existe intenção pendente válida na fila
  - Sem pendência válida, a primária remota volta a ser a fonte de verdade após sync
  - Seleção de imagem da Home/Detalhe prioriza fonte renderizável (`cached_uri` -> `local_uri` -> remota)

- **Compatibilidade de UI mobile**
  - Substituição de `SafeAreaView` legado por `react-native-safe-area-context` onde aplicável
  - Ajuste de `expo-image-picker` para sintaxe atual de `mediaTypes`

- **Home mobile (offline-first)**
  - Busca local passa a filtrar instantaneamente por caractere em memória
  - Botão de refresh rotulado explicitamente como **Sincronizar** para separar UX de busca e sync
  - Cards continuam vindo do SQLite local, com sync via API em background/manual

- **Fluxo de detalhe e edição**
  - Botão `Editar` deixou de ser placeholder e agora abre formulário funcional
  - Edição textual/financeira/minimos via `PUT /api/v1/products/{id}`
  - `quantidadeAtual` permanece auditável por ajuste dedicado (`stock-adjustments`)

- **Segurança backend**
  - `SecurityConfig` atualizado para liberar:
    - `GET /api/v1/validation/my` para `USER` e `ADMIN`
    - endpoints de imagem e ajuste de estoque para `USER` e `ADMIN`
  - Mantida proteção admin-only para fila administrativa de validação (`/api/v1/validation/**` exceto `/my`)

- **Contrato de criação de produto alinhado com o schema de estoque**
  - `ProductCreateDTO` atualizado com:
    - validação obrigatória de `descricao` e `precoVenda`
    - novo campo `quantidadeMinima` com validação `>= 0`
  - `ProductService.createProduct()` passa a persistir `quantidadeMinima`
  - Fluxo mobile de submit ajustado para enviar:
    - `precoCusto`, `precoVenda`
    - `quantidadeInicial`, `quantidadeMinima`
    - `referencia` e `codigoBarras` editáveis no formulário

- **Design system mobile**
  - `AppButton` e `AppInput` atualizados para usar `tailwind-merge`
  - Evita conflito de classes utilitárias do NativeWind em cenários de classes condicionais

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

- **Scan com IA e resposta de erro**
  - Antes: erros de quota do Gemini eram reportados de forma ambígua para o cliente
  - Agora: `429` é retornado explicitamente com mensagem orientativa para nova tentativa

- **Conflitos de imagem principal (`ux_product_images_primary_per_product`)**
  - Corrigidas condições de corrida em upload/troca/remoção de principal
  - Fluxos de aprovação de imagem em `ValidationService` alinhados com estratégia atômica

- **Cadastros em rede instável**
  - Melhor reconciliação de conflito por `referencia`/`codigoBarras`
  - Pós-sucesso remoto com falha local agora força sincronização sem perder o cadastro
  - Fluxo de retry no cadastro agora evita criação duplicada para o mesmo rascunho (`draftProductId` estável)

- **Render de imagem no app**
  - Corrigido cenário de “quadro vazio” com metadado sincronizado sem cache local válido
  - Melhor fallback entre URI de cache, URI local e endpoint remoto autenticado
  - Corrigido erro SQLite de placeholders (`18 values for 19 columns`) na persistência local de imagens

- **Conflitos e erros de integridade no backend**
  - Duplicidade de `referencia`/`codigo_barras` deixou de gerar erro genérico (`500`) e passa a retornar `409`
  - Overflow de campos financeiros (`markup/precos`) deixou de gerar erro genérico e passa a retornar `400`

- **Edição no detalhe mobile**
  - Corrigido fluxo onde o botão `Editar` não executava ação
  - Agora há submit real com tratamento online/offline e feedback de aprovação pendente

- **Imagem de produto no app**
  - Corrigido uso de `imagem_url` relativa com montagem da URL absoluta da API
  - Adicionado suporte de header `Authorization` no carregamento de conteúdo protegido

- **Compilação do backend**
  - `GlobalExceptionHandler` corrigido em mapeamento de métodos HTTP permitidos
  - Ajuste da referência de método para evitar erro de inferência de tipo no Java 21

- **UX do modal de cadastro no mobile**
  - Modal ajustado para manter formulário visível (altura fixa útil)
  - Correção de cenário em que apenas o título era exibido sem campos de edição
  - Mensagem de rascunho adicionada para evitar interpretação de "produto já cadastrado"

- **Validação de entrada no cadastro mobile**
  - Bloqueio explícito para salvar com `precoVenda` inválido/ausente
  - Alertas claros para `quantidadeInicial`, `quantidadeMinima` e `precoCusto` inválidos

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

- **Validação da Etapa 8.2.3**
  - ✅ `cd backend && mvn -q -DskipTests compile`
  - ✅ `cd backend && mvn -q -Dtest=ProductControllerTest,ProductImageServiceTest,ValidationServiceTest,SecurityAccessTest test`
  - ✅ `cd backend && mvn -q -Dtest=ScanControllerTest,GeminiServiceTest test`
  - ✅ `cd mobile/vision-stock-mobile && npx tsc --noEmit`

- **Validação da Etapa 8.1/8.2**
  - ✅ `npx tsc --noEmit` executado com sucesso em `mobile/vision-stock-mobile`
  - ✅ `mvn -q -DskipTests compile` executado com sucesso em `backend`
  - ✅ `mvn -q -Dtest=ProductControllerTest,SecurityAccessTest test` executado com sucesso em `backend`

- **Validação da Etapa 7**
  - ✅ `npx tsc --noEmit` executado com sucesso em `mobile/vision-stock-mobile`
  - ✅ `mvn -q -DskipTests compile` executado com sucesso em `backend`

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
