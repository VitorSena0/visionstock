# VisionStock Database Schema Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          VISIONSTOCK DATABASE                               │
│                        PostgreSQL Multi-Schema                              │
└─────────────────────────────────────────────────────────────────────────────┘

┌──────────────────┐
│  SCHEMA: auth    │
└──────────────────┘
    │
    ├── roles
    │   ├── id (UUID) PK
    │   ├── name (VARCHAR) UNIQUE
    │   ├── description (TEXT)
    │   ├── permissions (JSONB)
    │   ├── created_at
    │   ├── updated_at
    │   └── deleted_at
    │
    └── users
        ├── id (UUID) PK
        ├── nome (VARCHAR)
        ├── email (VARCHAR) UNIQUE
        ├── senha_hash (VARCHAR)
        ├── role (VARCHAR) ← CHECK: 'ADMIN' | 'USER'
        ├── role_id (UUID) FK → roles.id
        ├── ativo (BOOLEAN)
        ├── ultimo_acesso
        ├── created_at
        ├── updated_at
        └── deleted_at

┌──────────────────┐
│ SCHEMA: inventory│
└──────────────────┘
    │
    ├── categories
    │   ├── id (UUID) PK
    │   ├── nome (VARCHAR) UNIQUE
    │   ├── descricao (TEXT)
    │   ├── parent_id (UUID) FK → categories.id  [Self-reference]
    │   ├── created_at
    │   ├── updated_at
    │   └── deleted_at
    │
    ├── products ⭐ [Core Table]
    │   ├── id (UUID) PK
    │   │
    │   ├── [Identification]
    │   ├── referencia (VARCHAR) UNIQUE
    │   ├── codigo_barras (VARCHAR) UNIQUE
    │   ├── imagem_url (TEXT)
    │   │
    │   ├── [Details]
    │   ├── descricao (TEXT)
    │   ├── tamanho (VARCHAR)
    │   ├── cor (VARCHAR)
    │   ├── marca (VARCHAR)
    │   ├── category_id (UUID) FK → categories.id
    │   │
    │   ├── [Financial] 💰
    │   ├── preco_custo (DECIMAL 10,2)
    │   ├── preco_venda (DECIMAL 10,2)
    │   ├── markup_percentual (DECIMAL 5,2) [GENERATED/CALCULATED]
    │   │
    │   ├── [Stock] 📦
    │   ├── quantidade_atual (INTEGER)
    │   ├── quantidade_minima (INTEGER)
    │   │
    │   ├── [Control]
    │   ├── status_ia (VARCHAR)
    │   ├── status_validacao (VARCHAR)
    │   ├── versao (INTEGER)
    │   ├── sync_status (VARCHAR)
    │   │
    │   ├── [Audit]
    │   ├── created_at
    │   ├── updated_at
    │   ├── deleted_at
    │   ├── created_by (UUID) FK → auth.users.id
    │   └── updated_by (UUID) FK → auth.users.id
    │
    └── validation_queue
        ├── id (UUID) PK
        ├── product_id (UUID) FK → products.id
        ├── user_id (UUID) FK → auth.users.id
        ├── status (VARCHAR) ← CHECK: 'PENDENTE' | 'APROVADO' | 'REJEITADO'
        ├── dados_anteriores (JSONB)
        ├── dados_novos (JSONB)
        ├── observacao (TEXT)
        ├── reviewed_by (UUID) FK → auth.users.id
        ├── reviewed_at
        ├── created_at
        ├── updated_at
        └── deleted_at

┌──────────────────┐
│ SCHEMA: finance  │
└──────────────────┘
    │
    └── stock_movements
        ├── id (UUID) PK
        ├── product_id (UUID) FK → inventory.products.id
        ├── user_id (UUID) FK → auth.users.id
        ├── tipo_movimento (VARCHAR) ← CHECK: 'ENTRADA' | 'VENDA' | 'AJUSTE' | 'PERDA' | 'DEVOLUCAO'
        ├── quantidade (INTEGER)
        ├── valor_unitario (DECIMAL 10,2)
        ├── valor_total (DECIMAL 10,2) [GENERATED/CALCULATED]
        ├── data_movimento
        ├── observacao (TEXT)
        ├── documento_referencia (VARCHAR)
        ├── sync_status (VARCHAR)
        ├── created_at
        ├── updated_at
        └── deleted_at

┌──────────────────┐
│ SCHEMA: system   │
└──────────────────┘
    │
    └── audit_logs
        ├── id (UUID) PK
        ├── user_id (UUID) FK → auth.users.id
        ├── tabela (VARCHAR)
        ├── registro_id (UUID)
        ├── acao (VARCHAR) ← CHECK: 'CREATE' | 'UPDATE' | 'DELETE' | ...
        ├── dados_anteriores (JSONB)
        ├── dados_novos (JSONB)
        ├── ip_address (INET)
        ├── user_agent (TEXT)
        └── timestamp

═══════════════════════════════════════════════════════════════════════

VIEWS (Analytics)

┌─────────────────────────────────────────────────────────────────┐
│ finance.vw_dashboard_lucratividade                              │
├─────────────────────────────────────────────────────────────────┤
│ Combines: inventory.products + inventory.categories             │
│ Shows: estoque_atual, custo_total_investido,                    │
│        potencial_venda_total, margem_bruta_valor                │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ inventory.vw_produtos_estoque_baixo                             │
├─────────────────────────────────────────────────────────────────┤
│ Products with: quantidade_atual <= quantidade_minima            │
│ Shows: quantidade_a_repor                                       │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ finance.vw_resumo_vendas                                        │
├─────────────────────────────────────────────────────────────────┤
│ Aggregates: stock_movements WHERE tipo_movimento = 'VENDA'     │
│ Groups by: data_venda, product                                 │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ inventory.vw_validacoes_pendentes                               │
├─────────────────────────────────────────────────────────────────┤
│ Shows: validation_queue WHERE status = 'PENDENTE'               │
│ For: Manager approval workflow                                  │
└─────────────────────────────────────────────────────────────────┘

═══════════════════════════════════════════════════════════════════════

TRIGGERS

1. update_updated_at_column()
   → Applied to: users, products, validation_queue, stock_movements
   → Action: Auto-update 'updated_at' on UPDATE

2. atualizar_estoque_produto()
   → Applied to: finance.stock_movements (AFTER INSERT)
   → Action: Auto-update products.quantidade_atual based on movement

3. criar_log_auditoria()
   → Applied to: products, users, stock_movements (AFTER INSERT/UPDATE/DELETE)
   → Action: Auto-create audit log in system.audit_logs

═══════════════════════════════════════════════════════════════════════

INDEXES (Performance Optimization)

AUTH:
- idx_users_email (email) WHERE deleted_at IS NULL
- idx_users_role (role) WHERE deleted_at IS NULL
- idx_users_ativo (ativo) WHERE deleted_at IS NULL

INVENTORY:
- idx_products_referencia (referencia) WHERE deleted_at IS NULL
- idx_products_codigo_barras (codigo_barras) WHERE deleted_at IS NULL
- idx_products_category (category_id) WHERE deleted_at IS NULL
- idx_products_sync_status (sync_status) WHERE deleted_at IS NULL
- idx_products_status_validacao (status_validacao) WHERE deleted_at IS NULL
- idx_products_quantidade_minima (quantidade_atual, quantidade_minima)
  WHERE deleted_at IS NULL AND quantidade_atual <= quantidade_minima
- idx_validation_queue_status (status) WHERE deleted_at IS NULL
- idx_validation_queue_product (product_id) WHERE deleted_at IS NULL
- idx_validation_queue_user (user_id) WHERE deleted_at IS NULL

FINANCE:
- idx_stock_movements_product (product_id) WHERE deleted_at IS NULL
- idx_stock_movements_user (user_id) WHERE deleted_at IS NULL
- idx_stock_movements_tipo (tipo_movimento) WHERE deleted_at IS NULL
- idx_stock_movements_data (data_movimento DESC) WHERE deleted_at IS NULL
- idx_stock_movements_sync (sync_status) WHERE deleted_at IS NULL

SYSTEM:
- idx_audit_logs_user (user_id)
- idx_audit_logs_tabela (tabela, registro_id)
- idx_audit_logs_timestamp (timestamp DESC)

═══════════════════════════════════════════════════════════════════════

KEY RELATIONSHIPS

┌──────────┐         ┌──────────┐         ┌──────────┐
│auth.users│◄────────│ products │────────►│categories│
└──────────┘         └──────────┘         └──────────┘
     ▲                    ▲                     ▲
     │                    │                     │
     │              ┌─────┴─────┐               │
     │              │           │               │
     │         ┌────┴────┐ ┌───┴───────┐       │
     │         │stock_   │ │validation_│       │
     └─────────│movements│ │queue      │       │
               └─────────┘ └───────────┘       │
                                                │
                                    (hierarchical self-reference)

═══════════════════════════════════════════════════════════════════════

STATISTICS

📊 Total Schemas:  4 (auth, inventory, finance, system)
📊 Total Tables:   7 (base tables)
📊 Total Views:    4 (analytics views)
📊 Total Triggers: 7 (automation)
📊 Total Indexes:  21 (performance)
📊 Total FKs:      12 (referential integrity)

═══════════════════════════════════════════════════════════════════════

FEATURES SUMMARY

✅ UUID v4 Primary Keys          - Offline-first ready
✅ Soft Deletes                   - Data recovery possible
✅ Audit Trail                    - Complete history tracking
✅ Multi-Schema Organization      - Security & modularity
✅ Financial Tracking             - Cost, price, margin
✅ Stock Management               - Real-time inventory
✅ Validation Workflow            - USER→ADMIN approval
✅ Analytics Views                - Business intelligence
✅ Automatic Triggers             - Stock & audit automation
✅ Performance Indexes            - Optimized queries
✅ Cross-Schema FK                - Data integrity
✅ JSONB for Flexibility          - Dynamic data structures

═══════════════════════════════════════════════════════════════════════

WORKFLOW EXAMPLES

1. PRODUCT REGISTRATION:
   Frontend → UUID → inventory.products → Trigger → system.audit_logs

2. STOCK MOVEMENT:
   User Action → finance.stock_movements → Trigger → 
   inventory.products.quantidade_atual updated

3. VALIDATION FLOW:
   USER edits → inventory.validation_queue (PENDENTE) →
   ADMIN reviews → Update status (APROVADO/REJEITADO) →
   If APROVADO: Apply changes to inventory.products

4. ANALYTICS:
   Query: finance.vw_dashboard_lucratividade →
   Display: Profit margins, stock value, potential revenue

═══════════════════════════════════════════════════════════════════════
```
