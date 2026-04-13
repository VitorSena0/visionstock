-- =====================================================
-- VISIONSTOCK - Etapa 8.2
-- Imagens estruturadas de produto + tipos de mudanca na validacao
-- Data: 2026-02-16
-- =====================================================

-- 1) Expand validation queue change typing
ALTER TABLE inventory.validation_queue
    ADD COLUMN IF NOT EXISTS change_type VARCHAR(30) NOT NULL DEFAULT 'PRODUCT_FIELDS';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_validation_change_type'
          AND conrelid = 'inventory.validation_queue'::regclass
    ) THEN
        ALTER TABLE inventory.validation_queue
            ADD CONSTRAINT chk_validation_change_type
            CHECK (change_type IN ('PRODUCT_FIELDS', 'PRODUCT_IMAGES', 'STOCK_ADJUSTMENT'));
    END IF;
END $$;

-- 2) Binary images table (official source of truth)
CREATE TABLE IF NOT EXISTS inventory.product_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES inventory.products(id) ON DELETE CASCADE,
    file_name VARCHAR(255),
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    image_data BYTEA NOT NULL,
    sha256 VARCHAR(64),
    width INTEGER,
    height INTEGER,
    is_primary BOOLEAN NOT NULL DEFAULT false,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_by UUID REFERENCES auth.users(id),
    updated_by UUID REFERENCES auth.users(id)
);

CREATE INDEX IF NOT EXISTS idx_product_images_product
    ON inventory.product_images(product_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_product_images_sync_lookup
    ON inventory.product_images(product_id, is_primary)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_product_images_primary_per_product
    ON inventory.product_images(product_id)
    WHERE is_primary = true AND deleted_at IS NULL;

-- 3) Validation staging for image requests from USERs
CREATE TABLE IF NOT EXISTS inventory.validation_image_staging (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    validation_request_id UUID NOT NULL REFERENCES inventory.validation_queue(id) ON DELETE CASCADE,
    operation VARCHAR(30) NOT NULL,
    target_image_id UUID,
    file_name VARCHAR(255),
    content_type VARCHAR(100),
    file_size BIGINT,
    image_data BYTEA,
    sha256 VARCHAR(64),
    width INTEGER,
    height INTEGER,
    is_primary BOOLEAN,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_by UUID REFERENCES auth.users(id),
    updated_by UUID REFERENCES auth.users(id),
    CONSTRAINT chk_validation_image_operation CHECK (operation IN ('ADD', 'SET_PRIMARY', 'DELETE'))
);

CREATE INDEX IF NOT EXISTS idx_validation_image_staging_request
    ON inventory.validation_image_staging(validation_request_id)
    WHERE deleted_at IS NULL;
