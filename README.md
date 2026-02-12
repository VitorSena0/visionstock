# VisionStock 🎯

O VisionStock é um aplicativo mobile-first para gerenciamento de estoque de vestuário que utiliza Inteligência Artificial para automatizar o cadastro de produtos via fotos de etiquetas.

## 🌟 Características Principais

- **Offline-First:** Funciona completamente sem internet, sincronizando depois
- **IA Integrada:** Google Gemini 1.5 Flash para OCR de etiquetas
- **Fallback Inteligente:** Google ML Kit para modo offline
- **Gestão Financeira:** Controle de custos, vendas e margem de lucro
- **Workflow de Validação:** Sistema de aprovação para edições de estoquistas
- **Multi-Schema Database:** Organização modular e segura com PostgreSQL

## 🏗️ Arquitetura

### Banco de Dados

O VisionStock utiliza PostgreSQL com arquitetura **multi-schema** para organização e segurança:

- **`auth`** - Autenticação e usuários (ADMIN/USER)
- **`inventory`** - Produtos, categorias e validações
- **`finance`** - Movimentações financeiras e histórico
- **`system`** - Auditoria e logs do sistema

**Características Técnicas:**
- ✅ UUIDs como chave primária (seguro para offline-first)
- ✅ Soft deletes em todas as tabelas
- ✅ Auditoria automática de todas as ações
- ✅ Views analíticas para dashboards
- ✅ Triggers para automação de estoque

### Documentação do Banco de Dados

📚 **[Documentação Completa](database/README.md)** - Arquitetura, tabelas e views  
🚀 **[Quick Start Guide](database/QUICKSTART.md)** - Instalação em 3 passos  
💡 **[Exemplos de Uso](database/examples.sql)** - Queries práticas e casos de uso

## 🚀 Começando

### Pré-requisitos

- PostgreSQL 12+
- Node.js 18+ ou Java 17+ (dependendo do backend escolhido)
- Git

### Instalação do Banco de Dados

```bash
# 1. Criar banco de dados
createdb -U postgres visionstock

# 2. Executar migrations
psql -U postgres -d visionstock -f database/migrations/001_create_visionstock_schema.sql

# 3. Validar instalação
sudo -u postgres bash database/validate_schema.sh
```

Para instruções detalhadas, consulte o [Quick Start Guide](database/QUICKSTART.md).

## 📊 Schemas e Estrutura

### Schema: `auth`
```sql
- roles              # Papéis (ADMIN, USER)
- users              # Usuários do sistema
```

### Schema: `inventory`
```sql
- categories         # Categorias hierárquicas
- products           # Produtos com info financeira
- validation_queue   # Fila de validações
```

### Schema: `finance`
```sql
- stock_movements    # Histórico de movimentações (ENTRADA/VENDA/AJUSTE/PERDA)
```

### Schema: `system`
```sql
- audit_logs         # Logs de auditoria completos
```

## 💼 Casos de Uso

### 1. Cadastro de Produto via IA
```
Foto da Etiqueta → Google Gemini → JSON Estruturado → Banco
```

### 2. Modo Offline
```
Foto da Etiqueta → ML Kit (local) → Tap-to-Fill UI → Salvo Local → Sync depois
```

### 3. Workflow de Validação
```
Estoquista Edita → Fila de Validação → Gerente Aprova/Rejeita → Aplicado
```

### 4. Dashboard Financeiro
```
View: vw_dashboard_lucratividade
- Custo total investido
- Potencial de venda
- Margem de lucro
- Status de estoque
```

## 🔐 Segurança

- **UUID v4** para todas as entidades (não sequencial)
- **Soft deletes** para recuperação de dados
- **Auditoria automática** de todas as ações críticas
- **Row-level permissions** via roles (ADMIN/USER)
- **Cross-schema foreign keys** para integridade

## 🎯 Roadmap

### Versão 1.0 (Atual)
- [x] Schema multi-database completo
- [x] Sistema de validação de edições
- [x] Controle financeiro e estoque
- [x] Views analíticas

### Versão 1.1 (Planejado)
- [ ] Backend API (Node.js ou Spring Boot)
- [ ] Frontend Mobile (React Native)
- [ ] Integração Google Gemini
- [ ] Sincronização offline-first
- [ ] Sistema de tags para produtos

### Versão 2.0 (Futuro)
- [ ] Integração Shopee/Mercado Livre
- [ ] Multi-tenancy (múltiplas lojas)
- [ ] App iOS/Android nativos
- [ ] Dashboard analytics avançado

## 📖 Documentação

- [Database README](database/README.md) - Documentação completa do banco
- [Quick Start](database/QUICKSTART.md) - Guia de instalação rápida
- [Examples SQL](database/examples.sql) - Exemplos práticos de queries
- [Validation Script](database/validate_schema.sh) - Script de validação

## 🤝 Contribuindo

Contribuições são bem-vindas! Para contribuir:

1. Fork o projeto
2. Crie uma branch para sua feature (`git checkout -b feature/AmazingFeature`)
3. Commit suas mudanças (`git commit -m 'Add some AmazingFeature'`)
4. Push para a branch (`git push origin feature/AmazingFeature`)
5. Abra um Pull Request

## 📝 Licença

Este projeto está sob licença MIT. Veja o arquivo `LICENSE` para mais detalhes.

## 👥 Autores

- **Vitor Sena** - [@VitorSena0](https://github.com/VitorSena0)

## 🙏 Agradecimentos

- Google Gemini 1.5 Flash por OCR avançado
- Google ML Kit para processamento offline
- PostgreSQL pela robustez e confiabilidade
- Comunidade open source

---

**VisionStock** - Gerenciamento de Estoque Inteligente 🚀
