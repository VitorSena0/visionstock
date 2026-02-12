# Changelog

Todas as mudanças notáveis deste projeto serão documentadas neste arquivo.

## [Unreleased] - 2026-02-12

### Adicionado

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

- **Configuração de variáveis de ambiente**
  - Arquivo `.env` para credenciais sensíveis
  - Arquivo `.env.example` como template

### Alterado

- **Modelo Gemini atualizado** de `gemini-1.5-flash` para `gemini-2.0-flash`
  - Modelo anterior foi descontinuado pelo Google

- **URL da API Gemini** corrigida de `v1` para `v1beta`
  - Versão `v1` não suporta todos os modelos

- **Tratamento de rate limiting** nos testes de integração
  - Testes são automaticamente pulados (skipped) quando há erro 429
  - Usa `assumeTrue()` ao invés de falhar o teste

### Corrigido

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
