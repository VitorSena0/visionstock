import * as SQLite from 'expo-sqlite';

let dbPromise: Promise<SQLite.SQLiteDatabase> | null = null;

export const getDatabase = async () => {
  if (!dbPromise) {
    dbPromise = SQLite.openDatabaseAsync('visionstock.db');
  }

  return dbPromise;
};

export const runMigrations = async () => {
  const db = await getDatabase();

  await db.execAsync('PRAGMA journal_mode = WAL;');

  await db.execAsync(`
    CREATE TABLE IF NOT EXISTS products (
      id TEXT PRIMARY KEY NOT NULL,
      referencia TEXT,
      codigo_barras TEXT,
      imagem_url TEXT,
      descricao TEXT NOT NULL,
      tamanho TEXT,
      cor TEXT,
      marca TEXT,
      category_id TEXT,
      preco_custo REAL,
      preco_venda REAL,
      markup_percentual REAL,
      quantidade_atual INTEGER NOT NULL DEFAULT 0,
      quantidade_minima INTEGER NOT NULL DEFAULT 0,
      status_ia TEXT,
      status_validacao TEXT,
      sync_status TEXT,
      created_at TEXT,
      updated_at TEXT,
      local_pending_changes INTEGER NOT NULL DEFAULT 0,
      last_pending_validation_id TEXT
    );

    CREATE TABLE IF NOT EXISTS product_images (
      id TEXT PRIMARY KEY NOT NULL,
      product_id TEXT NOT NULL,
      remote_image_id TEXT,
      local_uri TEXT,
      cached_uri TEXT,
      cache_status TEXT NOT NULL DEFAULT 'NONE',
      cache_updated_at TEXT,
      cache_error TEXT,
      mime_type TEXT,
      file_size INTEGER,
      width INTEGER,
      height INTEGER,
      sha256 TEXT,
      is_primary INTEGER NOT NULL DEFAULT 0,
      source_type TEXT NOT NULL DEFAULT 'POST_EDIT',
      sync_status TEXT NOT NULL DEFAULT 'PENDENTE',
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      deleted_at TEXT
    );

    CREATE TABLE IF NOT EXISTS sync_queue (
      id TEXT PRIMARY KEY NOT NULL,
      type TEXT NOT NULL,
      payload TEXT NOT NULL,
      created_at TEXT NOT NULL,
      retry_count INTEGER NOT NULL DEFAULT 0
    );

    CREATE TABLE IF NOT EXISTS pending_edits (
      id TEXT PRIMARY KEY NOT NULL,
      product_id TEXT NOT NULL,
      patch_json TEXT NOT NULL,
      validation_request_id TEXT,
      status TEXT NOT NULL DEFAULT 'PENDENTE_APROVACAO',
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL
    );

    CREATE INDEX IF NOT EXISTS idx_products_descricao ON products(descricao);
    CREATE INDEX IF NOT EXISTS idx_products_codigo_barras ON products(codigo_barras);
    CREATE INDEX IF NOT EXISTS idx_products_referencia ON products(referencia);
    CREATE INDEX IF NOT EXISTS idx_products_updated_at ON products(updated_at DESC);

    CREATE INDEX IF NOT EXISTS idx_product_images_product ON product_images(product_id);
    CREATE INDEX IF NOT EXISTS idx_product_images_sync ON product_images(sync_status);
    CREATE INDEX IF NOT EXISTS idx_pending_edits_product ON pending_edits(product_id);
  `);

  await ensureColumn(db, 'products', 'local_pending_changes', 'INTEGER NOT NULL DEFAULT 0');
  await ensureColumn(db, 'products', 'last_pending_validation_id', 'TEXT');
  await ensureColumn(db, 'sync_queue', 'retry_count', 'INTEGER NOT NULL DEFAULT 0');

  // Defensive compatibility migration for installs that created product_images
  // with an older subset of columns.
  await ensureColumn(db, 'product_images', 'remote_image_id', 'TEXT');
  await ensureColumn(db, 'product_images', 'local_uri', 'TEXT');
  await ensureColumn(db, 'product_images', 'cached_uri', 'TEXT');
  await ensureColumn(db, 'product_images', 'cache_status', "TEXT NOT NULL DEFAULT 'NONE'");
  await ensureColumn(db, 'product_images', 'cache_updated_at', 'TEXT');
  await ensureColumn(db, 'product_images', 'cache_error', 'TEXT');
  await ensureColumn(db, 'product_images', 'mime_type', 'TEXT');
  await ensureColumn(db, 'product_images', 'file_size', 'INTEGER');
  await ensureColumn(db, 'product_images', 'width', 'INTEGER');
  await ensureColumn(db, 'product_images', 'height', 'INTEGER');
  await ensureColumn(db, 'product_images', 'sha256', 'TEXT');
  await ensureColumn(db, 'product_images', 'is_primary', 'INTEGER NOT NULL DEFAULT 0');
  await ensureColumn(db, 'product_images', 'source_type', "TEXT NOT NULL DEFAULT 'POST_EDIT'");
  await ensureColumn(db, 'product_images', 'sync_status', "TEXT NOT NULL DEFAULT 'PENDENTE'");
  await ensureColumn(db, 'product_images', 'created_at', "TEXT NOT NULL DEFAULT ''");
  await ensureColumn(db, 'product_images', 'updated_at', "TEXT NOT NULL DEFAULT ''");
  await ensureColumn(db, 'product_images', 'deleted_at', 'TEXT');

  // Run indexes that depend on columns introduced by ALTER TABLE only after
  // the compatibility migration has executed for existing installs.
  await db.execAsync(`
    CREATE INDEX IF NOT EXISTS idx_products_pending ON products(local_pending_changes);
  `);
};

const ensureColumn = async (
  db: SQLite.SQLiteDatabase,
  tableName: string,
  columnName: string,
  definition: string,
) => {
  const columns = await db.getAllAsync<{ name: string }>(`PRAGMA table_info(${tableName})`);
  const exists = columns.some((column) => column.name === columnName);
  if (exists) {
    return;
  }

  await db.execAsync(`ALTER TABLE ${tableName} ADD COLUMN ${columnName} ${definition};`);
};
