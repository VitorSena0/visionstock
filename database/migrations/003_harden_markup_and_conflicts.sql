-- =====================================================
-- VISIONSTOCK - Etapa 8.2.3
-- Hardening para conflitos e overflow de markup
-- Data: 2026-02-17
-- =====================================================

-- Expandir faixa de markup calculado para reduzir estouro em cenários extremos.
ALTER TABLE inventory.products
    ALTER COLUMN markup_percentual TYPE DECIMAL(10,2);

