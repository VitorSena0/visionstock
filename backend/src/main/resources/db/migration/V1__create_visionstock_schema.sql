-- =====================================================
-- VISIONSTOCK - Sistema de Gerenciamento de Estoque
-- Script DDL PostgreSQL com Multi-Schemas
-- Versão: 1.0.0
-- Data: 2026-02-12
-- =====================================================

-- =====================================================
-- EXTENSÕES NECESSÁRIAS
-- =====================================================

-- Extensão para suporte a UUID
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Extensão para geração de UUID v4 (alternativa moderna)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =====================================================
-- CRIAÇÃO DOS SCHEMAS
-- =====================================================

CREATE SCHEMA IF NOT EXISTS auth;
COMMENT ON SCHEMA auth IS 'Schema para autenticação e gerenciamento de usuários';

CREATE SCHEMA IF NOT EXISTS inventory;
COMMENT ON SCHEMA inventory IS 'Schema para gerenciamento de produtos e estoque';

CREATE SCHEMA IF NOT EXISTS finance;
COMMENT ON SCHEMA finance IS 'Schema para operações financeiras e movimentações';

CREATE SCHEMA IF NOT EXISTS system;
COMMENT ON SCHEMA system IS 'Schema para logs de auditoria e configurações do sistema';

-- =====================================================
-- SCHEMA: AUTH
-- =====================================================

-- Tabela de Roles/Papéis
CREATE TABLE IF NOT EXISTS auth.roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    permissions JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

COMMENT ON TABLE auth.roles IS 'Papéis de usuários no sistema (ADMIN, USER)';
COMMENT ON COLUMN auth.roles.permissions IS 'Permissões específicas do papel em formato JSON';

-- Tabela de Usuários
CREATE TABLE IF NOT EXISTS auth.users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    role_id UUID REFERENCES auth.roles(id),
    ativo BOOLEAN DEFAULT true,
    ultimo_acesso TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_role CHECK (role IN ('ADMIN', 'USER'))
);

COMMENT ON TABLE auth.users IS 'Usuários do sistema (Gerentes e Estoquistas)';
COMMENT ON COLUMN auth.users.role IS 'Papel do usuário: ADMIN (Gerente) ou USER (Estoquista)';
COMMENT ON COLUMN auth.users.ativo IS 'Indica se o usuário está ativo no sistema';

-- =====================================================
-- SCHEMA: INVENTORY
-- =====================================================

-- Tabela de Categorias
CREATE TABLE IF NOT EXISTS inventory.categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome VARCHAR(100) NOT NULL UNIQUE,
    descricao TEXT,
    parent_id UUID REFERENCES inventory.categories(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

COMMENT ON TABLE inventory.categories IS 'Categorias de produtos (ex: Camisas, Calças, Vestidos)';
COMMENT ON COLUMN inventory.categories.parent_id IS 'Permite hierarquia de categorias (categoria pai)';

-- Tabela de Produtos
CREATE TABLE IF NOT EXISTS inventory.products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Identificação
    referencia VARCHAR(100) UNIQUE,
    codigo_barras VARCHAR(100) UNIQUE,
    imagem_url TEXT,
    
    -- Detalhes do Produto
    descricao TEXT NOT NULL,
    tamanho VARCHAR(10),
    cor VARCHAR(50),
    marca VARCHAR(100),
    category_id UUID REFERENCES inventory.categories(id),
    
    -- Financeiro
    preco_custo DECIMAL(10,2),
    preco_venda DECIMAL(10,2) NOT NULL,
    markup_percentual DECIMAL(5,2) GENERATED ALWAYS AS (
        CASE 
            WHEN preco_custo IS NOT NULL AND preco_custo > 0 
            THEN ((preco_venda - preco_custo) / preco_custo * 100)
            ELSE NULL
        END
    ) STORED,
    
    -- Estoque
    quantidade_atual INTEGER DEFAULT 0 NOT NULL,
    quantidade_minima INTEGER DEFAULT 0,
    
    -- Controle de IA e Sincronização
    status_ia VARCHAR(50) DEFAULT 'MANUAL',
    status_validacao VARCHAR(20) DEFAULT 'OK',
    versao INTEGER DEFAULT 1,
    sync_status VARCHAR(20) DEFAULT 'PENDENTE',
    
    -- Auditoria
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_by UUID REFERENCES auth.users(id),
    updated_by UUID REFERENCES auth.users(id),
    
    CONSTRAINT chk_quantidade_atual CHECK (quantidade_atual >= 0),
    CONSTRAINT chk_quantidade_minima CHECK (quantidade_minima >= 0),
    CONSTRAINT chk_preco_venda CHECK (preco_venda >= 0),
    CONSTRAINT chk_preco_custo CHECK (preco_custo IS NULL OR preco_custo >= 0),
    CONSTRAINT chk_status_ia CHECK (status_ia IN ('MANUAL', 'IA_PROCESSADO', 'IA_REVISADO', 'OFFLINE_ML_KIT')),
    CONSTRAINT chk_status_validacao CHECK (status_validacao IN ('OK', 'REVIEW', 'REJECTED')),
    CONSTRAINT chk_sync_status CHECK (sync_status IN ('PENDENTE', 'SYNCED', 'CONFLITO'))
);

COMMENT ON TABLE inventory.products IS 'Produtos do estoque com informações completas';
COMMENT ON COLUMN inventory.products.referencia IS 'Código de referência único do produto';
COMMENT ON COLUMN inventory.products.codigo_barras IS 'Código de barras EAN/UPC';
COMMENT ON COLUMN inventory.products.preco_custo IS 'Custo de aquisição da peça (pode ser NULL no cadastro inicial)';
COMMENT ON COLUMN inventory.products.preco_venda IS 'Preço de venda ao cliente';
COMMENT ON COLUMN inventory.products.markup_percentual IS 'Margem de lucro percentual calculada automaticamente';
COMMENT ON COLUMN inventory.products.quantidade_atual IS 'Estoque físico atual';
COMMENT ON COLUMN inventory.products.quantidade_minima IS 'Quantidade mínima para alertas de reposição';
COMMENT ON COLUMN inventory.products.status_ia IS 'Status do processamento de IA (MANUAL, IA_PROCESSADO, etc)';
COMMENT ON COLUMN inventory.products.status_validacao IS 'Status de validação (OK, REVIEW, REJECTED)';
COMMENT ON COLUMN inventory.products.versao IS 'Versão do registro para controle de sincronização';
COMMENT ON COLUMN inventory.products.sync_status IS 'Status de sincronização offline-first';

-- Tabela de Fila de Validação
CREATE TABLE IF NOT EXISTS inventory.validation_queue (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES inventory.products(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id),
    status VARCHAR(20) DEFAULT 'PENDENTE',
    dados_anteriores JSONB NOT NULL,
    dados_novos JSONB NOT NULL,
    observacao TEXT,
    reviewed_by UUID REFERENCES auth.users(id),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    
    CONSTRAINT chk_validation_status CHECK (status IN ('PENDENTE', 'APROVADO', 'REJEITADO'))
);

COMMENT ON TABLE inventory.validation_queue IS 'Fila de validação de edições feitas por estoquistas';
COMMENT ON COLUMN inventory.validation_queue.dados_anteriores IS 'Estado anterior do produto em formato JSON';
COMMENT ON COLUMN inventory.validation_queue.dados_novos IS 'Novo estado proposto do produto em formato JSON';
COMMENT ON COLUMN inventory.validation_queue.reviewed_by IS 'Gerente que aprovou ou rejeitou a alteração';

-- =====================================================
-- SCHEMA: FINANCE
-- =====================================================

-- Tabela de Movimentações de Estoque
CREATE TABLE IF NOT EXISTS finance.stock_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES inventory.products(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id),
    
    -- Tipo e Detalhes da Movimentação
    tipo_movimento VARCHAR(20) NOT NULL,
    quantidade INTEGER NOT NULL,
    valor_unitario DECIMAL(10,2) NOT NULL,
    valor_total DECIMAL(10,2) GENERATED ALWAYS AS (quantidade * valor_unitario) STORED,
    
    -- Informações Adicionais
    data_movimento TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    observacao TEXT,
    documento_referencia VARCHAR(100),
    
    -- Controle de Sincronização
    sync_status VARCHAR(20) DEFAULT 'PENDENTE',
    
    -- Auditoria
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    
    CONSTRAINT chk_tipo_movimento CHECK (tipo_movimento IN ('ENTRADA', 'VENDA', 'AJUSTE', 'PERDA', 'DEVOLUCAO')),
    CONSTRAINT chk_quantidade CHECK (quantidade != 0),
    CONSTRAINT chk_valor_unitario CHECK (valor_unitario >= 0),
    CONSTRAINT chk_sync_status_movement CHECK (sync_status IN ('PENDENTE', 'SYNCED', 'CONFLITO'))
);

COMMENT ON TABLE finance.stock_movements IS 'Histórico de todas as movimentações de estoque (entradas, vendas, ajustes)';
COMMENT ON COLUMN finance.stock_movements.tipo_movimento IS 'Tipo: ENTRADA (compra), VENDA, AJUSTE (inventário), PERDA, DEVOLUCAO';
COMMENT ON COLUMN finance.stock_movements.quantidade IS 'Quantidade movimentada (positivo para entrada, negativo para saída)';
COMMENT ON COLUMN finance.stock_movements.valor_unitario IS 'Valor unitário no momento da operação';
COMMENT ON COLUMN finance.stock_movements.valor_total IS 'Valor total da movimentação (calculado automaticamente)';
COMMENT ON COLUMN finance.stock_movements.documento_referencia IS 'Número de NF, recibo ou documento relacionado';

-- =====================================================
-- SCHEMA: SYSTEM
-- =====================================================

-- Tabela de Logs de Auditoria
CREATE TABLE IF NOT EXISTS system.audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id),
    tabela VARCHAR(100) NOT NULL,
    registro_id UUID NOT NULL,
    acao VARCHAR(50) NOT NULL,
    dados_anteriores JSONB,
    dados_novos JSONB,
    ip_address INET,
    user_agent TEXT,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_acao CHECK (acao IN ('CREATE', 'UPDATE', 'DELETE', 'LOGIN', 'LOGOUT', 'APROVACAO', 'REJEICAO'))
);

COMMENT ON TABLE system.audit_logs IS 'Registro de todas as ações importantes do sistema para auditoria';
COMMENT ON COLUMN system.audit_logs.tabela IS 'Nome da tabela afetada';
COMMENT ON COLUMN system.audit_logs.registro_id IS 'ID do registro afetado';
COMMENT ON COLUMN system.audit_logs.acao IS 'Tipo de ação realizada';

-- =====================================================
-- ÍNDICES PARA PERFORMANCE
-- =====================================================

-- Índices AUTH
CREATE INDEX IF NOT EXISTS idx_users_email ON auth.users(email) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_users_role ON auth.users(role) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_users_ativo ON auth.users(ativo) WHERE deleted_at IS NULL;

-- Índices INVENTORY
CREATE INDEX IF NOT EXISTS idx_products_referencia ON inventory.products(referencia) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_products_codigo_barras ON inventory.products(codigo_barras) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_products_category ON inventory.products(category_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_products_sync_status ON inventory.products(sync_status) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_products_status_validacao ON inventory.products(status_validacao) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_products_quantidade_minima ON inventory.products(quantidade_atual, quantidade_minima) 
    WHERE deleted_at IS NULL AND quantidade_atual <= quantidade_minima;

CREATE INDEX IF NOT EXISTS idx_validation_queue_status ON inventory.validation_queue(status) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_validation_queue_product ON inventory.validation_queue(product_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_validation_queue_user ON inventory.validation_queue(user_id) WHERE deleted_at IS NULL;

-- Índices FINANCE
CREATE INDEX IF NOT EXISTS idx_stock_movements_product ON finance.stock_movements(product_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_stock_movements_user ON finance.stock_movements(user_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_stock_movements_tipo ON finance.stock_movements(tipo_movimento) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_stock_movements_data ON finance.stock_movements(data_movimento DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_stock_movements_sync ON finance.stock_movements(sync_status) WHERE deleted_at IS NULL;

-- Índices SYSTEM
CREATE INDEX IF NOT EXISTS idx_audit_logs_user ON system.audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_tabela ON system.audit_logs(tabela, registro_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp ON system.audit_logs(timestamp DESC);

-- =====================================================
-- VIEWS DE ANÁLISE E RELATÓRIOS
-- =====================================================

-- View: Dashboard de Lucratividade
CREATE OR REPLACE VIEW finance.vw_dashboard_lucratividade AS
SELECT 
    p.id AS product_id,
    p.referencia,
    p.descricao AS produto,
    p.marca,
    c.nome AS categoria,
    p.quantidade_atual AS estoque_atual,
    p.preco_custo,
    p.preco_venda,
    p.markup_percentual,
    
    -- Custo Total Investido
    CASE 
        WHEN p.preco_custo IS NOT NULL 
        THEN (p.quantidade_atual * p.preco_custo)
        ELSE 0
    END AS custo_total_investido,
    
    -- Potencial de Venda
    (p.quantidade_atual * p.preco_venda) AS potencial_venda_total,
    
    -- Margem Bruta em Valor
    CASE 
        WHEN p.preco_custo IS NOT NULL 
        THEN (p.quantidade_atual * (p.preco_venda - p.preco_custo))
        ELSE NULL
    END AS margem_bruta_valor,
    
    -- Status
    CASE 
        WHEN p.quantidade_atual <= 0 THEN 'SEM_ESTOQUE'
        WHEN p.quantidade_atual <= p.quantidade_minima THEN 'ESTOQUE_BAIXO'
        ELSE 'ESTOQUE_OK'
    END AS status_estoque
    
FROM inventory.products p
LEFT JOIN inventory.categories c ON p.category_id = c.id
WHERE p.deleted_at IS NULL
ORDER BY potencial_venda_total DESC;

COMMENT ON VIEW finance.vw_dashboard_lucratividade IS 'Dashboard de lucratividade com análise financeira dos produtos em estoque';

-- View: Produtos com Alerta de Estoque Baixo
CREATE OR REPLACE VIEW inventory.vw_produtos_estoque_baixo AS
SELECT 
    p.id,
    p.referencia,
    p.descricao,
    p.marca,
    p.quantidade_atual,
    p.quantidade_minima,
    (p.quantidade_minima - p.quantidade_atual) AS quantidade_a_repor,
    p.preco_venda,
    c.nome AS categoria
FROM inventory.products p
LEFT JOIN inventory.categories c ON p.category_id = c.id
WHERE p.deleted_at IS NULL
    AND p.quantidade_atual <= p.quantidade_minima
ORDER BY quantidade_a_repor DESC;

COMMENT ON VIEW inventory.vw_produtos_estoque_baixo IS 'Produtos que precisam de reposição (estoque abaixo do mínimo)';

-- View: Resumo de Vendas por Período
CREATE OR REPLACE VIEW finance.vw_resumo_vendas AS
SELECT 
    DATE(sm.data_movimento) AS data_venda,
    p.id AS product_id,
    p.referencia,
    p.descricao AS produto,
    COUNT(*) AS quantidade_vendas,
    SUM(ABS(sm.quantidade)) AS quantidade_total_vendida,
    SUM(sm.valor_total) AS receita_total,
    AVG(sm.valor_unitario) AS preco_medio_venda
FROM finance.stock_movements sm
INNER JOIN inventory.products p ON sm.product_id = p.id
WHERE sm.deleted_at IS NULL
    AND sm.tipo_movimento = 'VENDA'
GROUP BY DATE(sm.data_movimento), p.id, p.referencia, p.descricao
ORDER BY data_venda DESC, receita_total DESC;

COMMENT ON VIEW finance.vw_resumo_vendas IS 'Resumo de vendas por produto e período';

-- View: Fila de Validação Pendente (Para Gerentes)
CREATE OR REPLACE VIEW inventory.vw_validacoes_pendentes AS
SELECT 
    vq.id AS validacao_id,
    vq.status,
    vq.created_at AS data_solicitacao,
    p.referencia,
    p.descricao AS produto,
    u.nome AS solicitado_por,
    u.email AS email_solicitante,
    vq.dados_anteriores,
    vq.dados_novos,
    vq.observacao
FROM inventory.validation_queue vq
INNER JOIN inventory.products p ON vq.product_id = p.id
INNER JOIN auth.users u ON vq.user_id = u.id
WHERE vq.deleted_at IS NULL
    AND vq.status = 'PENDENTE'
ORDER BY vq.created_at ASC;

COMMENT ON VIEW inventory.vw_validacoes_pendentes IS 'Fila de validações pendentes para aprovação de gerentes';

-- =====================================================
-- FUNÇÕES E TRIGGERS
-- =====================================================

-- Função para atualizar updated_at automaticamente
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Triggers para atualização automática de updated_at
CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_products_updated_at
    BEFORE UPDATE ON inventory.products
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_validation_queue_updated_at
    BEFORE UPDATE ON inventory.validation_queue
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_stock_movements_updated_at
    BEFORE UPDATE ON finance.stock_movements
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Função para atualizar estoque após movimentação
CREATE OR REPLACE FUNCTION atualizar_estoque_produto()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        -- Atualiza quantidade atual baseado no tipo de movimento
        UPDATE inventory.products
        SET quantidade_atual = quantidade_atual + NEW.quantidade
        WHERE id = NEW.product_id;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger para atualizar estoque automaticamente
CREATE TRIGGER trg_atualizar_estoque
    AFTER INSERT ON finance.stock_movements
    FOR EACH ROW
    EXECUTE FUNCTION atualizar_estoque_produto();

COMMENT ON FUNCTION atualizar_estoque_produto() IS 'Atualiza automaticamente o estoque do produto após movimentação';

-- Função para criar log de auditoria
CREATE OR REPLACE FUNCTION criar_log_auditoria()
RETURNS TRIGGER AS $$
DECLARE
    v_acao VARCHAR(50);
    v_tabela VARCHAR(100);
BEGIN
    -- Determina a ação
    IF TG_OP = 'INSERT' THEN
        v_acao := 'CREATE';
    ELSIF TG_OP = 'UPDATE' THEN
        v_acao := 'UPDATE';
    ELSIF TG_OP = 'DELETE' THEN
        v_acao := 'DELETE';
    END IF;
    
    -- Determina a tabela
    v_tabela := TG_TABLE_SCHEMA || '.' || TG_TABLE_NAME;
    
    -- Insere o log
    IF TG_OP = 'DELETE' THEN
        INSERT INTO system.audit_logs (tabela, registro_id, acao, dados_anteriores)
        VALUES (v_tabela, OLD.id, v_acao, to_jsonb(OLD));
        RETURN OLD;
    ELSE
        INSERT INTO system.audit_logs (tabela, registro_id, acao, dados_anteriores, dados_novos)
        VALUES (
            v_tabela, 
            NEW.id, 
            v_acao,
            CASE WHEN TG_OP = 'UPDATE' THEN to_jsonb(OLD) ELSE NULL END,
            to_jsonb(NEW)
        );
        RETURN NEW;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Triggers de auditoria para tabelas principais
CREATE TRIGGER trg_audit_products
    AFTER INSERT OR UPDATE OR DELETE ON inventory.products
    FOR EACH ROW
    EXECUTE FUNCTION criar_log_auditoria();

CREATE TRIGGER trg_audit_users
    AFTER INSERT OR UPDATE OR DELETE ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION criar_log_auditoria();

CREATE TRIGGER trg_audit_stock_movements
    AFTER INSERT OR UPDATE OR DELETE ON finance.stock_movements
    FOR EACH ROW
    EXECUTE FUNCTION criar_log_auditoria();

-- =====================================================
-- DADOS INICIAIS (SEEDS)
-- =====================================================

-- Inserir Roles Padrão
INSERT INTO auth.roles (name, description, permissions) VALUES
('ADMIN', 'Gerente com acesso total ao sistema', '{"produtos": ["create", "read", "update", "delete"], "usuarios": ["create", "read", "update", "delete"], "validacoes": ["approve", "reject"], "relatorios": ["read"]}'),
('USER', 'Estoquista com permissões limitadas', '{"produtos": ["create", "read", "update"], "relatorios": ["read"]}')
ON CONFLICT (name) DO NOTHING;

-- Inserir Categorias Padrão
INSERT INTO inventory.categories (nome, descricao) VALUES
('Camisetas', 'Camisetas masculinas e femininas'),
('Calças', 'Calças jeans, sociais e esportivas'),
('Vestidos', 'Vestidos casuais e formais'),
('Shorts', 'Shorts masculinos e femininos'),
('Jaquetas', 'Jaquetas e casacos'),
('Acessórios', 'Bonés, cintos e outros acessórios')
ON CONFLICT (nome) DO NOTHING;

-- =====================================================
-- PERMISSÕES E SEGURANÇA
-- =====================================================

-- Revogar acesso público aos schemas
REVOKE ALL ON SCHEMA auth FROM PUBLIC;
REVOKE ALL ON SCHEMA inventory FROM PUBLIC;
REVOKE ALL ON SCHEMA finance FROM PUBLIC;
REVOKE ALL ON SCHEMA system FROM PUBLIC;

-- Comentários finais
-- COMMENT ON DATABASE visionstock IS 'VisionStock - Sistema de Gerenciamento de Estoque com IA';
-- Nota: O comando acima deve ser executado manualmente substituindo 'visionstock' pelo nome do seu banco

-- =====================================================
-- FIM DO SCRIPT DDL
-- =====================================================

-- Para verificar a estrutura criada, execute:
-- SELECT schema_name FROM information_schema.schemata WHERE schema_name IN ('auth', 'inventory', 'finance', 'system');
-- SELECT table_schema, table_name FROM information_schema.tables WHERE table_schema IN ('auth', 'inventory', 'finance', 'system') ORDER BY table_schema, table_name;
