-- =====================================================
-- VISIONSTOCK - Seeds genéricos para testes
-- Arquivo: test-seeds-generic.sql
-- Objetivo: criar contas, papéis e dados mínimos para testes locais
-- Observação: o schema atual aceita apenas roles ADMIN e USER
-- =====================================================

BEGIN;

-- =====================================================
-- 0) EXTENSÕES AUXILIARES
-- =====================================================
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =====================================================
-- 1) auth.roles
-- =====================================================
INSERT INTO auth.roles (name, description, permissions)
VALUES
    (
        'ADMIN',
        'Administrador/Gerente com acesso de aprovação e gestão completa',
        jsonb_build_object(
            'can_manage_users', true,
            'can_manage_products', true,
            'can_approve_validation', true,
            'can_view_finance', true
        )
    ),
    (
        'USER',
        'Estoquista/operador com acesso operacional e submissão para validação',
        jsonb_build_object(
            'can_manage_users', false,
            'can_manage_products', true,
            'can_approve_validation', false,
            'can_view_finance', false
        )
    )
ON CONFLICT (name) DO UPDATE
SET description = EXCLUDED.description,
    permissions = EXCLUDED.permissions,
    updated_at = CURRENT_TIMESTAMP;

-- =====================================================
-- 2) auth.users
-- Senha de teste comum: senha123
-- =====================================================
WITH admin_role AS (
    SELECT id FROM auth.roles WHERE name = 'ADMIN'
),
user_role AS (
    SELECT id FROM auth.roles WHERE name = 'USER'
)
INSERT INTO auth.users (
    id,
    nome,
    email,
    senha_hash,
    role,
    role_id,
    ativo
)
VALUES
    (
        '11111111-1111-1111-1111-111111111111',
        'Admin Teste',
        'admin.teste@visionstock.local',
        crypt('senha123', gen_salt('bf')),
        'ADMIN',
        (SELECT id FROM admin_role),
        true
    ),
    (
        '22222222-2222-2222-2222-222222222222',
        'Estoquista Teste',
        'estoquista.teste@visionstock.local',
        crypt('senha123', gen_salt('bf')),
        'USER',
        (SELECT id FROM user_role),
        true
    ),
    (
        '33333333-3333-3333-3333-333333333333',
        'Admin Secundário',
        'admin.secundario@visionstock.local',
        crypt('senha123', gen_salt('bf')),
        'ADMIN',
        (SELECT id FROM admin_role),
        true
    )
ON CONFLICT (email) DO UPDATE
SET nome = EXCLUDED.nome,
    senha_hash = EXCLUDED.senha_hash,
    role = EXCLUDED.role,
    role_id = EXCLUDED.role_id,
    ativo = EXCLUDED.ativo,
    updated_at = CURRENT_TIMESTAMP;

-- =====================================================
-- 3) inventory.categories
-- =====================================================
INSERT INTO inventory.categories (id, nome, descricao, parent_id)
VALUES
    (
        '44444444-4444-4444-4444-444444444444',
        'Camisetas',
        'Categoria principal de camisetas',
        NULL
    ),
    (
        '55555555-5555-5555-5555-555555555555',
        'Calças',
        'Categoria principal de calças',
        NULL
    ),
    (
        '66666666-6666-6666-6666-666666666666',
        'Vestidos',
        'Categoria principal de vestidos',
        NULL
    ),
    (
        '77777777-7777-7777-7777-777777777777',
        'Camisetas Polo',
        'Subcategoria de camisetas',
        '44444444-4444-4444-4444-444444444444'
    )
ON CONFLICT (nome) DO UPDATE
SET descricao = EXCLUDED.descricao,
    parent_id = EXCLUDED.parent_id,
    updated_at = CURRENT_TIMESTAMP;

-- =====================================================
-- 4) inventory.products
-- =====================================================
INSERT INTO inventory.products (
    id,
    referencia,
    codigo_barras,
    imagem_url,
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
    status_validacao,
    versao,
    sync_status,
    created_by,
    updated_by
)
VALUES
    (
        '88888888-8888-8888-8888-888888888888',
        'CAM-POLO-001',
        '7891234567890',
        NULL,
        'Camiseta Polo Masculina Premium',
        'M',
        'Azul Marinho',
        'Marca Exemplo',
        '77777777-7777-7777-7777-777777777777',
        45.00,
        99.90,
        50,
        10,
        'IA_PROCESSADO',
        'OK',
        1,
        'SYNCED',
        '11111111-1111-1111-1111-111111111111',
        '11111111-1111-1111-1111-111111111111'
    ),
    (
        '99999999-9999-9999-9999-999999999999',
        'CAL-JEANS-001',
        '7891234567891',
        NULL,
        'Calça Jeans Skinny Feminina',
        '38',
        'Azul',
        'Marca Exemplo',
        '55555555-5555-5555-5555-555555555555',
        65.00,
        149.90,
        30,
        5,
        'MANUAL',
        'REVIEW',
        1,
        'PENDENTE',
        '22222222-2222-2222-2222-222222222222',
        '22222222-2222-2222-2222-222222222222'
    ),
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        'VES-CAS-001',
        '7891234567892',
        NULL,
        'Vestido Casual Floral',
        'P',
        'Floral',
        'Marca Exemplo',
        '66666666-6666-6666-6666-666666666666',
        80.00,
        179.90,
        12,
        3,
        'IA_REVISADO',
        'OK',
        1,
        'SYNCED',
        '11111111-1111-1111-1111-111111111111',
        '11111111-1111-1111-1111-111111111111'
    )
ON CONFLICT (referencia) DO UPDATE
SET codigo_barras = EXCLUDED.codigo_barras,
    imagem_url = EXCLUDED.imagem_url,
    descricao = EXCLUDED.descricao,
    tamanho = EXCLUDED.tamanho,
    cor = EXCLUDED.cor,
    marca = EXCLUDED.marca,
    category_id = EXCLUDED.category_id,
    preco_custo = EXCLUDED.preco_custo,
    preco_venda = EXCLUDED.preco_venda,
    quantidade_atual = EXCLUDED.quantidade_atual,
    quantidade_minima = EXCLUDED.quantidade_minima,
    status_ia = EXCLUDED.status_ia,
    status_validacao = EXCLUDED.status_validacao,
    versao = EXCLUDED.versao,
    sync_status = EXCLUDED.sync_status,
    updated_by = EXCLUDED.updated_by,
    updated_at = CURRENT_TIMESTAMP;

-- =====================================================
-- 5) inventory.validation_queue
-- =====================================================
INSERT INTO inventory.validation_queue (
    id,
    product_id,
    user_id,
    status,
    dados_anteriores,
    dados_novos,
    observacao,
    reviewed_by,
    reviewed_at,
    change_type
)
VALUES
    (
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
        '99999999-9999-9999-9999-999999999999',
        '22222222-2222-2222-2222-222222222222',
        'PENDENTE',
        jsonb_build_object(
            'preco_venda', 149.90,
            'cor', 'Azul',
            'quantidade_minima', 5
        ),
        jsonb_build_object(
            'preco_venda', 139.90,
            'cor', 'Azul Escuro',
            'quantidade_minima', 8
        ),
        'Ajuste para simular fluxo de aprovação de produto',
        NULL,
        NULL,
        'PRODUCT_FIELDS'
    )
ON CONFLICT (id) DO UPDATE
SET product_id = EXCLUDED.product_id,
    user_id = EXCLUDED.user_id,
    status = EXCLUDED.status,
    dados_anteriores = EXCLUDED.dados_anteriores,
    dados_novos = EXCLUDED.dados_novos,
    observacao = EXCLUDED.observacao,
    reviewed_by = EXCLUDED.reviewed_by,
    reviewed_at = EXCLUDED.reviewed_at,
    change_type = EXCLUDED.change_type,
    updated_at = CURRENT_TIMESTAMP;

-- =====================================================
-- 6) inventory.product_images
-- =====================================================
INSERT INTO inventory.product_images (
    id,
    product_id,
    file_name,
    content_type,
    file_size,
    image_data,
    sha256,
    width,
    height,
    is_primary,
    sort_order,
    created_by,
    updated_by
)
VALUES
    (
        'cccccccc-cccc-cccc-cccc-cccccccccccc',
        '88888888-8888-8888-8888-888888888888',
        'cam-po-lo-001.png',
        'image/png',
        16,
        decode(repeat('41', 16), 'hex'),
        repeat('a1', 32),
        800,
        800,
        true,
        0,
        '11111111-1111-1111-1111-111111111111',
        '11111111-1111-1111-1111-111111111111'
    ),
    (
        'dddddddd-dddd-dddd-dddd-dddddddddddd',
        '99999999-9999-9999-9999-999999999999',
        'cal-jeans-001.jpg',
        'image/jpeg',
        32,
        decode(repeat('42', 32), 'hex'),
        repeat('b2', 32),
        1024,
        768,
        true,
        0,
        '22222222-2222-2222-2222-222222222222',
        '22222222-2222-2222-2222-222222222222'
    )
ON CONFLICT (id) DO UPDATE
SET product_id = EXCLUDED.product_id,
    file_name = EXCLUDED.file_name,
    content_type = EXCLUDED.content_type,
    file_size = EXCLUDED.file_size,
    image_data = EXCLUDED.image_data,
    sha256 = EXCLUDED.sha256,
    width = EXCLUDED.width,
    height = EXCLUDED.height,
    is_primary = EXCLUDED.is_primary,
    sort_order = EXCLUDED.sort_order,
    updated_by = EXCLUDED.updated_by,
    updated_at = CURRENT_TIMESTAMP;

-- =====================================================
-- 7) inventory.validation_image_staging
-- =====================================================
INSERT INTO inventory.validation_image_staging (
    id,
    validation_request_id,
    operation,
    target_image_id,
    file_name,
    content_type,
    file_size,
    image_data,
    sha256,
    width,
    height,
    is_primary,
    created_by,
    updated_by
)
VALUES
    (
        'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
        'ADD',
        NULL,
        'nova-imagem-cam-polo.png',
        'image/png',
        24,
        decode(repeat('43', 24), 'hex'),
        repeat('c3', 32),
        1200,
        1200,
        true,
        '22222222-2222-2222-2222-222222222222',
        '22222222-2222-2222-2222-222222222222'
    )
ON CONFLICT (id) DO UPDATE
SET validation_request_id = EXCLUDED.validation_request_id,
    operation = EXCLUDED.operation,
    target_image_id = EXCLUDED.target_image_id,
    file_name = EXCLUDED.file_name,
    content_type = EXCLUDED.content_type,
    file_size = EXCLUDED.file_size,
    image_data = EXCLUDED.image_data,
    sha256 = EXCLUDED.sha256,
    width = EXCLUDED.width,
    height = EXCLUDED.height,
    is_primary = EXCLUDED.is_primary,
    updated_by = EXCLUDED.updated_by,
    updated_at = CURRENT_TIMESTAMP;

-- =====================================================
-- 8) finance.stock_movements
-- =====================================================
INSERT INTO finance.stock_movements (
    id,
    product_id,
    user_id,
    tipo_movimento,
    quantidade,
    valor_unitario,
    observacao,
    documento_referencia,
    sync_status
)
VALUES
    (
        'ffffffff-ffff-ffff-ffff-ffffffffffff',
        '88888888-8888-8888-8888-888888888888',
        '11111111-1111-1111-1111-111111111111',
        'ENTRADA',
        20,
        45.00,
        'Entrada inicial de estoque para testes',
        'NF-TESTE-001',
        'SYNCED'
    ),
    (
        '12121212-1212-1212-1212-121212121212',
        '88888888-8888-8888-8888-888888888888',
        '22222222-2222-2222-2222-222222222222',
        'VENDA',
        -3,
        99.90,
        'Venda de teste no balcão',
        'PDV-TESTE-001',
        'PENDENTE'
    ),
    (
        '13131313-1313-1313-1313-131313131313',
        '99999999-9999-9999-9999-999999999999',
        '11111111-1111-1111-1111-111111111111',
        'AJUSTE',
        -2,
        0.00,
        'Ajuste após contagem física',
        'AJ-TESTE-001',
        'PENDENTE'
    ),
    (
        '14141414-1414-1414-1414-141414141414',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        '22222222-2222-2222-2222-222222222222',
        'DEVOLUCAO',
        1,
        179.90,
        'Devolução de cliente para teste',
        'DEV-TESTE-001',
        'CONFLITO'
    )
ON CONFLICT (id) DO UPDATE
SET product_id = EXCLUDED.product_id,
    user_id = EXCLUDED.user_id,
    tipo_movimento = EXCLUDED.tipo_movimento,
    quantidade = EXCLUDED.quantidade,
    valor_unitario = EXCLUDED.valor_unitario,
    observacao = EXCLUDED.observacao,
    documento_referencia = EXCLUDED.documento_referencia,
    sync_status = EXCLUDED.sync_status,
    updated_at = CURRENT_TIMESTAMP;

-- =====================================================
-- 9) system.audit_logs
-- =====================================================
INSERT INTO system.audit_logs (
    id,
    user_id,
    tabela,
    registro_id,
    acao,
    dados_anteriores,
    dados_novos,
    ip_address,
    user_agent
)
VALUES
    (
        '15151515-1515-1515-1515-151515151515',
        '11111111-1111-1111-1111-111111111111',
        'auth.users',
        '22222222-2222-2222-2222-222222222222',
        'CREATE',
        NULL,
        jsonb_build_object(
            'nome', 'Estoquista Teste',
            'email', 'estoquista.teste@visionstock.local',
            'role', 'USER'
        ),
        '127.0.0.1',
        'VisionStock Test Suite'
    ),
    (
        '16161616-1616-1616-1616-161616161616',
        '11111111-1111-1111-1111-111111111111',
        'inventory.products',
        '99999999-9999-9999-9999-999999999999',
        'UPDATE',
        jsonb_build_object('preco_venda', 149.90),
        jsonb_build_object('preco_venda', 139.90),
        '127.0.0.1',
        'VisionStock Test Suite'
    ),
    (
        '17171717-1717-1717-1717-171717171717',
        '22222222-2222-2222-2222-222222222222',
        'inventory.validation_queue',
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
        'APROVACAO',
        jsonb_build_object('status', 'PENDENTE'),
        jsonb_build_object('status', 'APROVADO'),
        '127.0.0.1',
        'VisionStock Test Suite'
    )
ON CONFLICT (id) DO UPDATE
SET user_id = EXCLUDED.user_id,
    tabela = EXCLUDED.tabela,
    registro_id = EXCLUDED.registro_id,
    acao = EXCLUDED.acao,
    dados_anteriores = EXCLUDED.dados_anteriores,
    dados_novos = EXCLUDED.dados_novos,
    ip_address = EXCLUDED.ip_address,
    user_agent = EXCLUDED.user_agent,
    timestamp = CURRENT_TIMESTAMP;

COMMIT;

-- =====================================================
-- CONSULTAS RÁPIDAS DE VALIDAÇÃO
-- =====================================================
SELECT 'auth.roles' AS tabela, COUNT(*) AS total FROM auth.roles
UNION ALL
SELECT 'auth.users', COUNT(*) FROM auth.users
UNION ALL
SELECT 'inventory.categories', COUNT(*) FROM inventory.categories
UNION ALL
SELECT 'inventory.products', COUNT(*) FROM inventory.products
UNION ALL
SELECT 'inventory.validation_queue', COUNT(*) FROM inventory.validation_queue
UNION ALL
SELECT 'inventory.product_images', COUNT(*) FROM inventory.product_images
UNION ALL
SELECT 'inventory.validation_image_staging', COUNT(*) FROM inventory.validation_image_staging
UNION ALL
SELECT 'finance.stock_movements', COUNT(*) FROM finance.stock_movements
UNION ALL
SELECT 'system.audit_logs', COUNT(*) FROM system.audit_logs;
