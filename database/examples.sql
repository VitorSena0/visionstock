-- =====================================================
-- VISIONSTOCK - Exemplos de Uso do Banco de Dados
-- Arquivo: examples.sql
-- =====================================================

-- Este arquivo contém exemplos práticos de como usar o banco de dados VisionStock
-- Execute cada seção separadamente conforme necessário

-- =====================================================
-- 1. CRIAÇÃO DE USUÁRIOS
-- =====================================================

-- Criar um Gerente (ADMIN)
INSERT INTO auth.users (
    nome, 
    email, 
    senha_hash, 
    role, 
    ativo
) VALUES (
    'João Silva',
    'joao.silva@visionstock.com',
    '$2b$10$abcdefghijklmnopqrstuvwxyz123456',  -- Hash bcrypt da senha
    'ADMIN',
    true
) RETURNING id, nome, role;

-- Criar um Estoquista (USER)
INSERT INTO auth.users (
    nome, 
    email, 
    senha_hash, 
    role, 
    ativo
) VALUES (
    'Maria Santos',
    'maria.santos@visionstock.com',
    '$2b$10$zyxwvutsrqponmlkjihgfedcba654321',
    'USER',
    true
) RETURNING id, nome, role;

-- Listar todos os usuários ativos
SELECT 
    id,
    nome,
    email,
    role,
    ativo,
    created_at
FROM auth.users
WHERE deleted_at IS NULL
ORDER BY created_at DESC;

-- =====================================================
-- 2. GESTÃO DE CATEGORIAS
-- =====================================================

-- Criar categorias principais
INSERT INTO inventory.categories (nome, descricao) VALUES
    ('Camisetas', 'Camisetas masculinas e femininas'),
    ('Calças', 'Calças jeans, sociais e esportivas'),
    ('Vestidos', 'Vestidos casuais e formais')
ON CONFLICT (nome) DO NOTHING;

-- Criar subcategorias (hierarquia)
INSERT INTO inventory.categories (nome, descricao, parent_id)
SELECT 
    'Camisetas Polo',
    'Camisetas estilo polo',
    id
FROM inventory.categories
WHERE nome = 'Camisetas';

-- Listar categorias com hierarquia
SELECT 
    c1.id,
    c1.nome,
    c1.descricao,
    c2.nome AS categoria_pai
FROM inventory.categories c1
LEFT JOIN inventory.categories c2 ON c1.parent_id = c2.id
WHERE c1.deleted_at IS NULL
ORDER BY c2.nome, c1.nome;

-- =====================================================
-- 3. CADASTRO DE PRODUTOS
-- =====================================================

-- Cadastrar produto completo (com custo e preço)
INSERT INTO inventory.products (
    referencia,
    codigo_barras,
    descricao,
    tamanho,
    cor,
    marca,
    category_id,
    preco_custo,
    preco_venda,
    quantidade_atual,
    quantidade_minima,
    status_ia,
    created_by
) VALUES (
    'CAM-POLO-001',
    '7891234567890',
    'Camiseta Polo Masculina Premium',
    'M',
    'Azul Marinho',
    'Lacoste',
    (SELECT id FROM inventory.categories WHERE nome = 'Camisetas Polo' LIMIT 1),
    45.00,
    99.90,
    50,
    10,
    'IA_PROCESSADO',
    (SELECT id FROM auth.users WHERE role = 'ADMIN' LIMIT 1)
) RETURNING id, referencia, descricao, markup_percentual;

-- Cadastrar produto sem custo (será informado depois)
INSERT INTO inventory.products (
    referencia,
    descricao,
    tamanho,
    cor,
    marca,
    preco_venda,
    quantidade_atual,
    quantidade_minima,
    status_ia,
    created_by
) VALUES (
    'CAL-JEANS-001',
    'Calça Jeans Skinny Feminina',
    '38',
    'Azul',
    'Levi''s',
    149.90,
    30,
    5,
    'MANUAL',
    (SELECT id FROM auth.users WHERE role = 'USER' LIMIT 1)
) RETURNING id, referencia, descricao;

-- Atualizar custo do produto posteriormente
UPDATE inventory.products
SET preco_custo = 65.00,
    updated_by = (SELECT id FROM auth.users WHERE role = 'ADMIN' LIMIT 1)
WHERE referencia = 'CAL-JEANS-001';

-- Listar produtos com margem de lucro
SELECT 
    p.referencia,
    p.descricao,
    p.marca,
    p.tamanho,
    p.cor,
    p.quantidade_atual,
    p.preco_custo,
    p.preco_venda,
    p.markup_percentual AS "margem_%",
    c.nome AS categoria
FROM inventory.products p
LEFT JOIN inventory.categories c ON p.category_id = c.id
WHERE p.deleted_at IS NULL
ORDER BY p.markup_percentual DESC;

-- =====================================================
-- 4. MOVIMENTAÇÕES DE ESTOQUE
-- =====================================================

-- 4.1 ENTRADA DE MERCADORIA (Compra)
INSERT INTO finance.stock_movements (
    product_id,
    user_id,
    tipo_movimento,
    quantidade,
    valor_unitario,
    observacao,
    documento_referencia
) VALUES (
    (SELECT id FROM inventory.products WHERE referencia = 'CAM-POLO-001'),
    (SELECT id FROM auth.users WHERE role = 'ADMIN' LIMIT 1),
    'ENTRADA',
    100,  -- Positivo = entrada
    45.00,
    'Compra de fornecedor - Primeira remessa',
    'NF-123456'
) RETURNING id, tipo_movimento, quantidade, valor_total;

-- 4.2 VENDA DE PRODUTO
INSERT INTO finance.stock_movements (
    product_id,
    user_id,
    tipo_movimento,
    quantidade,
    valor_unitario,
    observacao
) VALUES (
    (SELECT id FROM inventory.products WHERE referencia = 'CAM-POLO-001'),
    (SELECT id FROM auth.users WHERE role = 'USER' LIMIT 1),
    'VENDA',
    -3,  -- Negativo = saída
    99.90,
    'Venda no balcão - Cliente João'
) RETURNING id, tipo_movimento, quantidade, valor_total;

-- 4.3 AJUSTE DE INVENTÁRIO
INSERT INTO finance.stock_movements (
    product_id,
    user_id,
    tipo_movimento,
    quantidade,
    valor_unitario,
    observacao
) VALUES (
    (SELECT id FROM inventory.products WHERE referencia = 'CAM-POLO-001'),
    (SELECT id FROM auth.users WHERE role = 'ADMIN' LIMIT 1),
    'AJUSTE',
    -2,  -- Ajuste negativo (contagem física menor que sistema)
    0.00,
    'Ajuste após inventário físico'
) RETURNING id, tipo_movimento, quantidade;

-- 4.4 PERDA/QUEBRA
INSERT INTO finance.stock_movements (
    product_id,
    user_id,
    tipo_movimento,
    quantidade,
    valor_unitario,
    observacao
) VALUES (
    (SELECT id FROM inventory.products WHERE referencia = 'CAM-POLO-001'),
    (SELECT id FROM auth.users WHERE role = 'USER' LIMIT 1),
    'PERDA',
    -1,
    45.00,  -- Valor do custo (para cálculo de prejuízo)
    'Produto danificado - manchado'
) RETURNING id, tipo_movimento, quantidade, valor_total;

-- Listar movimentações de um produto
SELECT 
    sm.data_movimento,
    sm.tipo_movimento,
    sm.quantidade,
    sm.valor_unitario,
    sm.valor_total,
    sm.observacao,
    u.nome AS usuario,
    p.quantidade_atual AS estoque_atual
FROM finance.stock_movements sm
INNER JOIN inventory.products p ON sm.product_id = p.id
INNER JOIN auth.users u ON sm.user_id = u.id
WHERE p.referencia = 'CAM-POLO-001'
    AND sm.deleted_at IS NULL
ORDER BY sm.data_movimento DESC;

-- =====================================================
-- 5. FILA DE VALIDAÇÃO (Workflow Estoquista -> Gerente)
-- =====================================================

-- 5.1 Estoquista tenta editar produto (cria validação)
INSERT INTO inventory.validation_queue (
    product_id,
    user_id,
    dados_anteriores,
    dados_novos,
    observacao
) VALUES (
    (SELECT id FROM inventory.products WHERE referencia = 'CAM-POLO-001'),
    (SELECT id FROM auth.users WHERE role = 'USER' LIMIT 1),
    jsonb_build_object(
        'preco_venda', 99.90,
        'cor', 'Azul Marinho',
        'quantidade_minima', 10
    ),
    jsonb_build_object(
        'preco_venda', 89.90,
        'cor', 'Azul Royal',
        'quantidade_minima', 15
    ),
    'Cliente reclamou do preço. Cor estava incorreta no sistema.'
) RETURNING id, status, created_at;

-- 5.2 Listar validações pendentes (visão do gerente)
SELECT * FROM inventory.vw_validacoes_pendentes;

-- 5.3 Gerente APROVA a validação
UPDATE inventory.validation_queue
SET status = 'APROVADO',
    reviewed_by = (SELECT id FROM auth.users WHERE role = 'ADMIN' LIMIT 1),
    reviewed_at = CURRENT_TIMESTAMP
WHERE id = 'UUID-DA-VALIDACAO'  -- Substituir pelo ID real
    AND status = 'PENDENTE';

-- 5.4 Aplicar as alterações aprovadas ao produto
UPDATE inventory.products
SET preco_venda = 89.90,
    cor = 'Azul Royal',
    quantidade_minima = 15,
    updated_by = (SELECT id FROM auth.users WHERE role = 'ADMIN' LIMIT 1)
WHERE referencia = 'CAM-POLO-001';

-- 5.5 Gerente REJEITA a validação
UPDATE inventory.validation_queue
SET status = 'REJEITADO',
    reviewed_by = (SELECT id FROM auth.users WHERE role = 'ADMIN' LIMIT 1),
    reviewed_at = CURRENT_TIMESTAMP,
    observacao = 'Preço já está competitivo. Cor conferida - está correta.'
WHERE id = 'UUID-DA-VALIDACAO'
    AND status = 'PENDENTE';

-- =====================================================
-- 6. CONSULTAS E RELATÓRIOS
-- =====================================================

-- 6.1 Dashboard de Lucratividade
SELECT 
    produto,
    marca,
    categoria,
    estoque_atual,
    preco_custo,
    preco_venda,
    markup_percentual AS "margem_%",
    custo_total_investido AS "custo_total",
    potencial_venda_total AS "potencial_venda",
    margem_bruta_valor AS "lucro_estimado",
    status_estoque
FROM finance.vw_dashboard_lucratividade
WHERE estoque_atual > 0
ORDER BY margem_bruta_valor DESC
LIMIT 20;

-- 6.2 Produtos com estoque baixo (precisam reposição)
SELECT 
    referencia,
    descricao,
    marca,
    quantidade_atual,
    quantidade_minima,
    quantidade_a_repor,
    categoria
FROM inventory.vw_produtos_estoque_baixo
ORDER BY quantidade_a_repor DESC;

-- 6.3 Resumo de vendas dos últimos 30 dias
SELECT 
    data_venda,
    produto,
    quantidade_total_vendida,
    receita_total,
    preco_medio_venda
FROM finance.vw_resumo_vendas
WHERE data_venda >= CURRENT_DATE - INTERVAL '30 days'
ORDER BY data_venda DESC, receita_total DESC;

-- 6.4 Top 10 produtos mais vendidos (quantidade)
SELECT 
    p.referencia,
    p.descricao,
    p.marca,
    SUM(ABS(sm.quantidade)) AS total_vendido,
    SUM(sm.valor_total) AS receita_total
FROM finance.stock_movements sm
INNER JOIN inventory.products p ON sm.product_id = p.id
WHERE sm.tipo_movimento = 'VENDA'
    AND sm.deleted_at IS NULL
    AND sm.data_movimento >= CURRENT_DATE - INTERVAL '90 days'
GROUP BY p.id, p.referencia, p.descricao, p.marca
ORDER BY total_vendido DESC
LIMIT 10;

-- 6.5 Produtos sem movimento nos últimos 60 dias
SELECT 
    p.referencia,
    p.descricao,
    p.marca,
    p.quantidade_atual,
    p.preco_venda,
    MAX(sm.data_movimento) AS ultima_movimentacao
FROM inventory.products p
LEFT JOIN finance.stock_movements sm ON p.id = sm.product_id
WHERE p.deleted_at IS NULL
    AND p.quantidade_atual > 0
GROUP BY p.id, p.referencia, p.descricao, p.marca, p.quantidade_atual, p.preco_venda
HAVING MAX(sm.data_movimento) < CURRENT_DATE - INTERVAL '60 days'
    OR MAX(sm.data_movimento) IS NULL
ORDER BY p.quantidade_atual DESC;

-- 6.6 Análise de margem por categoria
SELECT 
    c.nome AS categoria,
    COUNT(p.id) AS total_produtos,
    SUM(p.quantidade_atual) AS estoque_total,
    AVG(p.markup_percentual) AS margem_media,
    SUM(CASE WHEN p.preco_custo IS NOT NULL 
        THEN p.quantidade_atual * p.preco_custo 
        ELSE 0 END) AS investimento_total,
    SUM(p.quantidade_atual * p.preco_venda) AS potencial_venda
FROM inventory.products p
INNER JOIN inventory.categories c ON p.category_id = c.id
WHERE p.deleted_at IS NULL
    AND p.quantidade_atual > 0
GROUP BY c.id, c.nome
ORDER BY potencial_venda DESC;

-- =====================================================
-- 7. AUDITORIA E LOGS
-- =====================================================

-- Listar ações recentes de um usuário
SELECT 
    al.timestamp,
    al.acao,
    al.tabela,
    al.dados_novos->>'descricao' AS detalhe,
    u.nome AS usuario
FROM system.audit_logs al
LEFT JOIN auth.users u ON al.user_id = u.id
WHERE al.user_id = 'UUID-DO-USUARIO'  -- Substituir pelo ID real
ORDER BY al.timestamp DESC
LIMIT 50;

-- Histórico de alterações de um produto específico
SELECT 
    al.timestamp,
    al.acao,
    u.nome AS usuario,
    al.dados_anteriores,
    al.dados_novos
FROM system.audit_logs al
LEFT JOIN auth.users u ON al.user_id = u.id
WHERE al.tabela = 'inventory.products'
    AND al.registro_id = 'UUID-DO-PRODUTO'  -- Substituir pelo ID real
ORDER BY al.timestamp DESC;

-- Ações de aprovação/rejeição de validações
SELECT 
    al.timestamp,
    al.acao,
    u.nome AS gerente,
    al.dados_novos->>'status' AS decisao
FROM system.audit_logs al
INNER JOIN auth.users u ON al.user_id = u.id
WHERE al.tabela = 'inventory.validation_queue'
    AND al.acao IN ('UPDATE')
    AND (al.dados_novos->>'status' = 'APROVADO' 
         OR al.dados_novos->>'status' = 'REJEITADO')
ORDER BY al.timestamp DESC;

-- =====================================================
-- 8. SOFT DELETE E RECUPERAÇÃO
-- =====================================================

-- Soft delete de um produto
UPDATE inventory.products
SET deleted_at = CURRENT_TIMESTAMP
WHERE referencia = 'CAM-POLO-001';

-- Recuperar produto excluído (desfazer soft delete)
UPDATE inventory.products
SET deleted_at = NULL
WHERE referencia = 'CAM-POLO-001';

-- Listar produtos excluídos (soft deleted)
SELECT 
    referencia,
    descricao,
    deleted_at,
    updated_by
FROM inventory.products
WHERE deleted_at IS NOT NULL
ORDER BY deleted_at DESC;

-- =====================================================
-- 9. MANUTENÇÃO E PERFORMANCE
-- =====================================================

-- Verificar produtos com dados inconsistentes
SELECT 
    referencia,
    descricao,
    quantidade_atual,
    preco_custo,
    preco_venda
FROM inventory.products
WHERE deleted_at IS NULL
    AND (
        preco_venda <= 0
        OR quantidade_atual < 0
        OR (preco_custo IS NOT NULL AND preco_custo > preco_venda)
    );

-- Reindexar tabelas para melhor performance
REINDEX TABLE inventory.products;
REINDEX TABLE finance.stock_movements;

-- Analisar estatísticas das tabelas
ANALYZE inventory.products;
ANALYZE finance.stock_movements;

-- Vacuum para recuperar espaço
VACUUM ANALYZE inventory.products;
VACUUM ANALYZE finance.stock_movements;

-- =====================================================
-- 10. LIMPEZA DE DADOS ANTIGOS
-- =====================================================

-- Deletar permanentemente registros soft-deleted há mais de 1 ano
DELETE FROM inventory.products
WHERE deleted_at < CURRENT_DATE - INTERVAL '1 year';

DELETE FROM inventory.validation_queue
WHERE deleted_at < CURRENT_DATE - INTERVAL '1 year';

-- Arquivar logs de auditoria antigos (mais de 2 anos)
-- Criar tabela de arquivo antes
CREATE TABLE IF NOT EXISTS system.audit_logs_archive (LIKE system.audit_logs);

-- Mover logs antigos
INSERT INTO system.audit_logs_archive
SELECT * FROM system.audit_logs
WHERE timestamp < CURRENT_DATE - INTERVAL '2 years';

-- Remover da tabela principal
DELETE FROM system.audit_logs
WHERE timestamp < CURRENT_DATE - INTERVAL '2 years';

-- =====================================================
-- FIM DOS EXEMPLOS
-- =====================================================
