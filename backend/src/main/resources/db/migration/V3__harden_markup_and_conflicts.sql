-- =====================================================
-- VISIONSTOCK - Etapa 8.2.3
-- Hardening para conflitos e overflow de markup
-- Data: 2026-02-17
-- =====================================================

-- Drop view that depends on the column
DROP VIEW IF EXISTS finance.vw_dashboard_lucratividade;

-- Expandir faixa de markup calculado para reduzir estouro em cenários extremos.
ALTER TABLE inventory.products
    ALTER COLUMN markup_percentual TYPE DECIMAL(10,2);

-- Recreate the view
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
