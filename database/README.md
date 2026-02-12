# VisionStock - Database Documentation

## Visão Geral

Este diretório contém os scripts de migração e documentação do banco de dados PostgreSQL do projeto VisionStock, um sistema de gerenciamento de estoque de vestuário com IA.

## Arquitetura do Banco de Dados

### Multi-Schema Organization

O banco de dados está organizado em **4 schemas** principais para melhor organização, segurança e manutenibilidade:

#### 1. **Schema: `auth`**
Gerenciamento de autenticação e usuários.

**Tabelas:**
- `roles` - Papéis/Funções (ADMIN, USER)
- `users` - Usuários do sistema (Gerentes e Estoquistas)

#### 2. **Schema: `inventory`**
Gerenciamento de produtos e estoque.

**Tabelas:**
- `categories` - Categorias de produtos (hierárquicas)
- `products` - Produtos com informações completas (financeiro + estoque)
- `validation_queue` - Fila de validação de edições de estoquistas

#### 3. **Schema: `finance`**
Operações financeiras e movimentações.

**Tabelas:**
- `stock_movements` - Histórico de movimentações (ENTRADA, VENDA, AJUSTE, PERDA)

#### 4. **Schema: `system`**
Logs e auditoria do sistema.

**Tabelas:**
- `audit_logs` - Registro completo de todas as ações importantes

---

## Características Técnicas

### 🔑 UUIDs como Chave Primária
Todas as tabelas utilizam **UUID v4** como chave primária para garantir:
- Geração segura de IDs no frontend (Offline-First)
- Ausência de conflitos em sincronização
- Segurança (não sequenciais)

```sql
id UUID PRIMARY KEY DEFAULT gen_random_uuid()
```

### 🗑️ Soft Deletes
Todas as tabelas de negócio possuem a coluna `deleted_at`:
- Permite recuperação de dados
- Mantém integridade referencial
- Histórico completo de auditoria

```sql
deleted_at TIMESTAMP WITH TIME ZONE
```

### 📊 Auditoria Completa
Todas as tabelas possuem:
```sql
created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
deleted_at TIMESTAMP WITH TIME ZONE
```

### 💰 Tipos Monetários
Todos os valores financeiros utilizam `DECIMAL(10,2)` para evitar erros de arredondamento:
```sql
preco_custo DECIMAL(10,2)
preco_venda DECIMAL(10,2) NOT NULL
```

### 🔄 Sincronização Offline-First
Coluna `sync_status` em tabelas críticas:
- `PENDENTE` - Aguardando sincronização
- `SYNCED` - Sincronizado com sucesso
- `CONFLITO` - Requer resolução manual

---

## Estrutura das Tabelas Principais

### Tabela: `inventory.products`

Armazena todas as informações dos produtos, incluindo dados financeiros e de estoque.

**Campos Principais:**
- **Identificação:** `id`, `referencia`, `codigo_barras`, `imagem_url`
- **Detalhes:** `descricao`, `tamanho`, `cor`, `marca`, `category_id`
- **Financeiro:**
  - `preco_custo` - Custo de aquisição (pode ser NULL)
  - `preco_venda` - Preço de venda ao cliente
  - `markup_percentual` - Calculado automaticamente: `((preco_venda - preco_custo) / preco_custo * 100)`
- **Estoque:**
  - `quantidade_atual` - Saldo físico atual
  - `quantidade_minima` - Para alertas de reposição
- **Controle:**
  - `status_ia` - Status do processamento de IA
  - `status_validacao` - Status de validação (OK, REVIEW, REJECTED)
  - `versao` - Controle de versão para sincronização

### Tabela: `finance.stock_movements`

Registra todas as movimentações de estoque para análise financeira e rastreabilidade.

**Tipos de Movimento:**
- `ENTRADA` - Compra/Entrada de mercadoria
- `VENDA` - Venda de produto
- `AJUSTE` - Ajuste de inventário
- `PERDA` - Perda/Quebra de produto
- `DEVOLUCAO` - Devolução de cliente

**Campos Calculados:**
- `valor_total` - Calculado automaticamente: `quantidade * valor_unitario`

### Tabela: `inventory.validation_queue`

Implementa o fluxo de validação de edições feitas por estoquistas (USER).

**Fluxo:**
1. Estoquista (USER) edita um produto → Cria registro com status `PENDENTE`
2. Gerente (ADMIN) revisa → Aprova ou Rejeita
3. Se aprovado → Aplica alterações ao produto
4. Se rejeitado → Mantém produto original

---

## Views de Análise

### View: `finance.vw_dashboard_lucratividade`

Dashboard completo de lucratividade dos produtos em estoque.

**Colunas:**
- `produto` - Nome do produto
- `estoque_atual` - Quantidade em estoque
- `preco_custo` / `preco_venda` - Preços
- `markup_percentual` - Margem percentual
- `custo_total_investido` - `quantidade_atual * preco_custo`
- `potencial_venda_total` - `quantidade_atual * preco_venda`
- `margem_bruta_valor` - Lucro estimado se vender todo estoque
- `status_estoque` - SEM_ESTOQUE / ESTOQUE_BAIXO / ESTOQUE_OK

**Exemplo de Uso:**
```sql
SELECT * FROM finance.vw_dashboard_lucratividade
WHERE status_estoque = 'ESTOQUE_OK'
ORDER BY margem_bruta_valor DESC
LIMIT 10;
```

### View: `inventory.vw_produtos_estoque_baixo`

Lista produtos que precisam de reposição (abaixo do mínimo).

**Exemplo de Uso:**
```sql
SELECT * FROM inventory.vw_produtos_estoque_baixo
ORDER BY quantidade_a_repor DESC;
```

### View: `finance.vw_resumo_vendas`

Resumo de vendas por produto e período.

**Exemplo de Uso:**
```sql
SELECT * FROM finance.vw_resumo_vendas
WHERE data_venda >= CURRENT_DATE - INTERVAL '30 days'
ORDER BY receita_total DESC;
```

### View: `inventory.vw_validacoes_pendentes`

Fila de validações pendentes para gerentes aprovarem.

**Exemplo de Uso:**
```sql
SELECT * FROM inventory.vw_validacoes_pendentes;
```

---

## Triggers e Automações

### 1. **Atualização Automática de `updated_at`**
Todas as tabelas principais possuem trigger que atualiza automaticamente o campo `updated_at` em qualquer UPDATE.

### 2. **Atualização Automática de Estoque**
Quando uma movimentação é inserida em `finance.stock_movements`, o campo `quantidade_atual` do produto é automaticamente atualizado.

**Exemplo:**
```sql
-- Inserir uma VENDA de 5 unidades
INSERT INTO finance.stock_movements (product_id, user_id, tipo_movimento, quantidade, valor_unitario)
VALUES ('uuid-do-produto', 'uuid-do-usuario', 'VENDA', -5, 49.90);
-- O campo quantidade_atual do produto será automaticamente decrementado em 5
```

### 3. **Auditoria Automática**
Todas as operações em tabelas críticas (`products`, `users`, `stock_movements`) geram automaticamente registros em `system.audit_logs`.

---

## Instalação e Execução

### Pré-requisitos
- PostgreSQL 12+ instalado
- Extensões: `uuid-ossp` e `pgcrypto`

### Executar o Script DDL

```bash
# Conectar ao PostgreSQL
psql -U postgres -d visionstock

# Executar o script de migração
\i database/migrations/001_create_visionstock_schema.sql
```

Ou via linha de comando:
```bash
psql -U postgres -d visionstock -f database/migrations/001_create_visionstock_schema.sql
```

### Verificar a Instalação

```sql
-- Listar schemas criados
SELECT schema_name 
FROM information_schema.schemata 
WHERE schema_name IN ('auth', 'inventory', 'finance', 'system');

-- Listar todas as tabelas
SELECT table_schema, table_name 
FROM information_schema.tables 
WHERE table_schema IN ('auth', 'inventory', 'finance', 'system')
ORDER BY table_schema, table_name;

-- Verificar views criadas
SELECT table_schema, table_name 
FROM information_schema.views 
WHERE table_schema IN ('finance', 'inventory');
```

---

## Exemplos de Uso

### 1. Cadastrar um Novo Produto

```sql
INSERT INTO inventory.products (
    referencia, 
    descricao, 
    tamanho, 
    cor, 
    marca, 
    preco_custo, 
    preco_venda, 
    quantidade_atual,
    quantidade_minima,
    created_by
) VALUES (
    'CAM-001',
    'Camiseta Polo Masculina',
    'M',
    'Azul',
    'Lacoste',
    45.00,
    99.90,
    20,
    5,
    'uuid-do-usuario'
);
```

### 2. Registrar uma Venda

```sql
-- Inserir movimentação de venda
INSERT INTO finance.stock_movements (
    product_id,
    user_id,
    tipo_movimento,
    quantidade,
    valor_unitario,
    observacao
) VALUES (
    'uuid-do-produto',
    'uuid-do-usuario',
    'VENDA',
    -2,  -- Negativo indica saída
    99.90,
    'Venda no balcão'
);
-- O estoque será automaticamente decrementado
```

### 3. Solicitar Validação de Edição (Estoquista)

```sql
INSERT INTO inventory.validation_queue (
    product_id,
    user_id,
    dados_anteriores,
    dados_novos,
    observacao
) VALUES (
    'uuid-do-produto',
    'uuid-do-estoquista',
    '{"preco_venda": 99.90, "cor": "Azul"}',
    '{"preco_venda": 89.90, "cor": "Azul Marinho"}',
    'Ajuste de preço e correção da cor'
);
```

### 4. Aprovar Validação (Gerente)

```sql
UPDATE inventory.validation_queue
SET status = 'APROVADO',
    reviewed_by = 'uuid-do-gerente',
    reviewed_at = CURRENT_TIMESTAMP
WHERE id = 'uuid-da-validacao';

-- Depois, aplicar as alterações ao produto
UPDATE inventory.products
SET preco_venda = 89.90,
    cor = 'Azul Marinho',
    updated_by = 'uuid-do-gerente'
WHERE id = 'uuid-do-produto';
```

### 5. Consultar Dashboard de Lucratividade

```sql
SELECT 
    produto,
    estoque_atual,
    preco_custo,
    preco_venda,
    markup_percentual,
    custo_total_investido,
    potencial_venda_total,
    margem_bruta_valor
FROM finance.vw_dashboard_lucratividade
WHERE estoque_atual > 0
ORDER BY margem_bruta_valor DESC
LIMIT 20;
```

---

## Boas Práticas

### 1. Sempre usar Soft Delete
```sql
-- ❌ Nunca faça isso:
DELETE FROM inventory.products WHERE id = 'uuid';

-- ✅ Faça isso:
UPDATE inventory.products 
SET deleted_at = CURRENT_TIMESTAMP 
WHERE id = 'uuid';
```

### 2. Incluir `deleted_at IS NULL` nas consultas
```sql
-- Para evitar trazer registros excluídos
SELECT * FROM inventory.products
WHERE deleted_at IS NULL;
```

### 3. Usar Transações para Operações Críticas
```sql
BEGIN;
    -- Inserir movimentação
    INSERT INTO finance.stock_movements (...) VALUES (...);
    
    -- Outras operações relacionadas
    UPDATE inventory.products SET status_validacao = 'OK' WHERE id = 'uuid';
COMMIT;
```

### 4. Respeitar Permissões por Role
- **ADMIN**: Acesso total
- **USER**: Não pode aprovar validações nem deletar produtos

---

## Segurança

### Princípios Implementados:

1. **Separação por Schemas** - Facilita controle de acesso granular
2. **UUID v4** - Evita enumeração e garante unicidade global
3. **Soft Deletes** - Permite recuperação e auditoria completa
4. **Auditoria Automática** - Rastreabilidade de todas as ações
5. **Constraints e Validações** - Integridade de dados garantida no BD

### Próximos Passos de Segurança:

- Implementar Row Level Security (RLS) no PostgreSQL
- Criar usuários de banco específicos por role
- Implementar política de backup automático
- Adicionar criptografia de campos sensíveis

---

## Manutenção

### Comandos Úteis

```sql
-- Verificar tamanho das tabelas
SELECT 
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables
WHERE schemaname IN ('auth', 'inventory', 'finance', 'system')
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;

-- Verificar índices não utilizados
SELECT * FROM pg_stat_user_indexes WHERE idx_scan = 0;

-- Limpar registros soft-deleted antigos (mais de 1 ano)
DELETE FROM inventory.products 
WHERE deleted_at < CURRENT_DATE - INTERVAL '1 year';
```

---

## Roadmap

### Versão 1.1 (Planejado)
- [ ] Tabela de histórico de preços
- [ ] Tabela de fornecedores
- [ ] Integração com e-commerce (Shopee/Mercado Livre)
- [ ] Sistema de tags para produtos
- [ ] Tabela de promoções e descontos

### Versão 2.0 (Futuro)
- [ ] Multi-tenancy (múltiplas lojas)
- [ ] Gestão de múltiplos estoques físicos
- [ ] Sistema de códigos de desconto
- [ ] Integração com gateways de pagamento

---

## Suporte

Para dúvidas ou problemas:
- Documentação: Este arquivo
- Issues: GitHub Issues do projeto
- Script DDL: `database/migrations/001_create_visionstock_schema.sql`

---

**VisionStock Database v1.0.0**  
*Desenvolvido com ❤️ para gerenciamento de estoque inteligente*
