import { getDatabase } from '.';
import type {
  LocalProduct,
  ProductResponseDTO,
  ProductSyncDTO,
  ProductUpdateDTO,
} from '../types/product';

type ProductRow = {
  id: string;
  referencia: string | null;
  codigoBarras: string | null;
  imagemUrl: string | null;
  serverImagemUrl: string | null;
  descricao: string;
  tamanho: string | null;
  cor: string | null;
  marca: string | null;
  categoryId: string | null;
  precoCusto: number | null;
  precoVenda: number | null;
  markupPercentual: number | null;
  quantidadeAtual: number | null;
  quantidadeMinima: number | null;
  statusIa: string | null;
  statusValidacao: string | null;
  syncStatus: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  localPendingChanges: number | null;
  lastPendingValidationId: string | null;
};

type PendingEditRow = {
  id: string;
  productId: string;
  patchJson: string;
  validationRequestId: string | null;
  status: string;
  createdAt: string;
  updatedAt: string;
};

const selectClause = `
  SELECT
    p.id,
    p.referencia,
    p.codigo_barras AS codigoBarras,
    COALESCE(
      (
        SELECT CASE
          WHEN pi.cached_uri IS NOT NULL
            AND pi.cached_uri <> ''
          THEN pi.cached_uri
          WHEN COALESCE(pi.sync_status, '') NOT IN ('PENDENTE', 'ERRO')
            AND pi.remote_image_id IS NOT NULL
            AND pi.remote_image_id <> ''
          THEN '/api/v1/products/' || p.id || '/images/' || pi.remote_image_id || '/content'
          WHEN pi.local_uri IS NOT NULL
            AND pi.local_uri <> ''
          THEN pi.local_uri
          WHEN pi.remote_image_id IS NOT NULL
            AND pi.remote_image_id <> ''
          THEN '/api/v1/products/' || p.id || '/images/' || pi.remote_image_id || '/content'
          ELSE NULL
        END
        FROM product_images pi
        WHERE pi.product_id = p.id
          AND pi.deleted_at IS NULL
        ORDER BY
          CASE
            WHEN pi.is_primary = 1 AND COALESCE(pi.sync_status, '') <> 'ERRO' THEN 0
            WHEN COALESCE(pi.sync_status, '') <> 'ERRO' THEN 1
            WHEN pi.is_primary = 1 THEN 2
            ELSE 3
          END,
          CASE
            WHEN pi.cached_uri IS NOT NULL AND pi.cached_uri <> '' THEN 0
            WHEN pi.local_uri IS NOT NULL AND pi.local_uri <> '' THEN 1
            WHEN pi.remote_image_id IS NOT NULL
              AND pi.remote_image_id <> ''
              AND COALESCE(pi.sync_status, '') <> 'ERRO'
            THEN 2
            WHEN pi.remote_image_id IS NOT NULL AND pi.remote_image_id <> '' THEN 3
            ELSE 4
          END,
          pi.updated_at DESC
        LIMIT 1
      ),
      p.imagem_url
    ) AS imagemUrl,
    p.imagem_url AS serverImagemUrl,
    p.descricao,
    p.tamanho,
    p.cor,
    p.marca,
    p.category_id AS categoryId,
    p.preco_custo AS precoCusto,
    p.preco_venda AS precoVenda,
    p.markup_percentual AS markupPercentual,
    p.quantidade_atual AS quantidadeAtual,
    p.quantidade_minima AS quantidadeMinima,
    p.status_ia AS statusIa,
    p.status_validacao AS statusValidacao,
    p.sync_status AS syncStatus,
    p.created_at AS createdAt,
    p.updated_at AS updatedAt,
    p.local_pending_changes AS localPendingChanges,
    p.last_pending_validation_id AS lastPendingValidationId
  FROM products p
`;

const toIsoNow = () => new Date().toISOString();

const mapRowToProduct = (row: ProductRow): LocalProduct => ({
  id: row.id,
  referencia: row.referencia,
  codigoBarras: row.codigoBarras,
  imagemUrl: row.imagemUrl,
  serverImagemUrl: row.serverImagemUrl,
  descricao: row.descricao,
  tamanho: row.tamanho,
  cor: row.cor,
  marca: row.marca,
  categoryId: row.categoryId,
  precoCusto: row.precoCusto,
  precoVenda: row.precoVenda,
  markupPercentual: row.markupPercentual,
  quantidadeAtual: row.quantidadeAtual ?? 0,
  quantidadeMinima: row.quantidadeMinima ?? 0,
  statusIa: row.statusIa,
  statusValidacao: row.statusValidacao,
  syncStatus: row.syncStatus,
  createdAt: row.createdAt,
  updatedAt: row.updatedAt,
  localPendingChanges: row.localPendingChanges ?? 0,
  lastPendingValidationId: row.lastPendingValidationId,
});

const normalizeSearch = (search?: string) => search?.trim().toLowerCase() ?? '';

const toProductSyncDTO = (product: ProductSyncDTO | ProductResponseDTO): ProductSyncDTO => ({
  id: product.id ?? null,
  referencia: product.referencia ?? null,
  codigoBarras: product.codigoBarras ?? null,
  imagemUrl: product.imagemUrl ?? null,
  descricao: product.descricao ?? null,
  tamanho: product.tamanho ?? null,
  cor: product.cor ?? null,
  marca: product.marca ?? null,
  categoryId: product.categoryId ?? null,
  precoCusto: 'precoCusto' in product ? product.precoCusto ?? null : null,
  precoVenda: product.precoVenda ?? null,
  markupPercentual: 'markupPercentual' in product ? product.markupPercentual ?? null : null,
  quantidadeAtual: product.quantidadeAtual ?? 0,
  quantidadeMinima: product.quantidadeMinima ?? 0,
  statusIa: product.statusIa ?? null,
  statusValidacao: product.statusValidacao ?? null,
  syncStatus: product.syncStatus ?? null,
  createdAt: product.createdAt ?? null,
  updatedAt: product.updatedAt ?? null,
});

const parsePendingPatch = (
  patchJson: string,
): (ProductUpdateDTO & { quantidadeDelta?: number; motivo?: string }) | null => {
  try {
    return JSON.parse(patchJson) as ProductUpdateDTO & {
      quantidadeDelta?: number;
      motivo?: string;
    };
  } catch {
    return null;
  }
};

export const productRepository = {
  async saveBatch(products: ProductSyncDTO[]): Promise<void> {
    if (products.length === 0) {
      return;
    }

    const db = await getDatabase();

    await db.withTransactionAsync(async () => {
      for (const product of products) {
        if (!product.id) {
          continue;
        }

        await db.runAsync(
          `
            INSERT INTO products (
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
              markup_percentual,
              quantidade_atual,
              quantidade_minima,
              status_ia,
              status_validacao,
              sync_status,
              created_at,
              updated_at,
              local_pending_changes,
              last_pending_validation_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NULL)
            ON CONFLICT(id) DO UPDATE SET
              referencia = excluded.referencia,
              codigo_barras = excluded.codigo_barras,
              imagem_url = excluded.imagem_url,
              descricao = excluded.descricao,
              tamanho = excluded.tamanho,
              cor = excluded.cor,
              marca = excluded.marca,
              category_id = excluded.category_id,
              preco_custo = excluded.preco_custo,
              preco_venda = excluded.preco_venda,
              markup_percentual = excluded.markup_percentual,
              quantidade_atual = excluded.quantidade_atual,
              quantidade_minima = excluded.quantidade_minima,
              status_ia = excluded.status_ia,
              status_validacao = excluded.status_validacao,
              sync_status = excluded.sync_status,
              created_at = excluded.created_at,
              updated_at = excluded.updated_at
          `,
          [
            product.id,
            product.referencia ?? null,
            product.codigoBarras ?? null,
            product.imagemUrl ?? null,
            product.descricao?.trim() || 'Sem descricao',
            product.tamanho ?? null,
            product.cor ?? null,
            product.marca ?? null,
            product.categoryId ?? null,
            product.precoCusto ?? null,
            product.precoVenda ?? null,
            product.markupPercentual ?? null,
            product.quantidadeAtual ?? 0,
            product.quantidadeMinima ?? 0,
            product.statusIa ?? null,
            product.statusValidacao ?? null,
            product.syncStatus ?? null,
            product.createdAt ?? null,
            product.updatedAt ?? null,
          ],
        );
      }
    });
  },

  async saveOne(product: ProductSyncDTO | ProductResponseDTO): Promise<void> {
    await this.saveBatch([toProductSyncDTO(product)]);
  },

  async getAll(search?: string): Promise<LocalProduct[]> {
    const db = await getDatabase();
    const normalizedSearch = normalizeSearch(search);

    if (!normalizedSearch) {
      const rows = await db.getAllAsync<ProductRow>(
        `
          ${selectClause}
          ORDER BY COALESCE(updated_at, created_at) DESC, descricao ASC
        `,
      );
      return rows.map(mapRowToProduct);
    }

    const likeSearch = `%${normalizedSearch}%`;
    const rows = await db.getAllAsync<ProductRow>(
      `
        ${selectClause}
        WHERE
          LOWER(descricao) LIKE ?
          OR LOWER(COALESCE(referencia, '')) LIKE ?
          OR LOWER(COALESCE(codigo_barras, '')) LIKE ?
        ORDER BY COALESCE(updated_at, created_at) DESC, descricao ASC
      `,
      [likeSearch, likeSearch, likeSearch],
    );
    return rows.map(mapRowToProduct);
  },

  async getById(id: string): Promise<LocalProduct | null> {
    const db = await getDatabase();
    const row = await db.getFirstAsync<ProductRow>(
      `
        ${selectClause}
        WHERE id = ?
        LIMIT 1
      `,
      [id],
    );
    return row ? mapRowToProduct(row) : null;
  },

  async applyLocalProductUpdate(
    productId: string,
    patch: ProductUpdateDTO,
    validationRequestId?: string | null,
  ): Promise<void> {
    const current = await this.getById(productId);
    if (!current) {
      return;
    }

    const now = toIsoNow();
    await this.upsertPendingEdit(productId, patch, validationRequestId ?? null);

    await this.updateProductRow({
      ...current,
      referencia: patch.referencia ?? current.referencia,
      codigoBarras: patch.codigoBarras ?? current.codigoBarras,
      descricao: patch.descricao ?? current.descricao,
      tamanho: patch.tamanho ?? current.tamanho,
      cor: patch.cor ?? current.cor,
      marca: patch.marca ?? current.marca,
      precoCusto:
        patch.precoCusto !== undefined ? patch.precoCusto : current.precoCusto ?? null,
      precoVenda:
        patch.precoVenda !== undefined ? patch.precoVenda : current.precoVenda ?? null,
      quantidadeMinima:
        patch.quantidadeMinima !== undefined
          ? patch.quantidadeMinima
          : current.quantidadeMinima ?? 0,
      statusValidacao: 'PENDENTE_APROVACAO',
      syncStatus: 'PENDENTE',
      localPendingChanges: 1,
      lastPendingValidationId: validationRequestId ?? current.lastPendingValidationId ?? null,
      updatedAt: now,
    });
  },

  async applyLocalStockAdjustment(
    productId: string,
    quantidadeDelta: number,
    motivo?: string,
    validationRequestId?: string | null,
  ): Promise<void> {
    const current = await this.getById(productId);
    if (!current) {
      return;
    }

    const nextQuantity = Math.max(0, (current.quantidadeAtual ?? 0) + quantidadeDelta);
    const now = toIsoNow();
    await this.upsertPendingEdit(
      productId,
      { quantidadeDelta, motivo },
      validationRequestId ?? null,
    );

    await this.updateProductRow({
      ...current,
      quantidadeAtual: nextQuantity,
      statusValidacao: 'PENDENTE_APROVACAO',
      syncStatus: 'PENDENTE',
      localPendingChanges: 1,
      lastPendingValidationId: validationRequestId ?? current.lastPendingValidationId ?? null,
      updatedAt: now,
    });
  },

  async clearPendingEditByValidation(validationRequestId: string): Promise<void> {
    const db = await getDatabase();
    const row = await db.getFirstAsync<PendingEditRow>(
      `
        SELECT
          id,
          product_id AS productId,
          patch_json AS patchJson,
          validation_request_id AS validationRequestId,
          status,
          created_at AS createdAt,
          updated_at AS updatedAt
        FROM pending_edits
        WHERE validation_request_id = ?
        LIMIT 1
      `,
      [validationRequestId],
    );
    if (!row) {
      return;
    }

    await db.withTransactionAsync(async () => {
      await db.runAsync(
        `
          DELETE FROM pending_edits
          WHERE id = ?
        `,
        [row.id],
      );
      await db.runAsync(
        `
          UPDATE products
          SET local_pending_changes = 0,
              last_pending_validation_id = NULL
          WHERE id = ?
        `,
        [row.productId],
      );
    });
  },

  async getPendingEdits(): Promise<
    Array<{
      id: string;
      productId: string;
      patch: ProductUpdateDTO & { quantidadeDelta?: number; motivo?: string };
      validationRequestId?: string | null;
    }>
  > {
    const db = await getDatabase();
    const rows = await db.getAllAsync<PendingEditRow>(
      `
        SELECT
          id,
          product_id AS productId,
          patch_json AS patchJson,
          validation_request_id AS validationRequestId,
          status,
          created_at AS createdAt,
          updated_at AS updatedAt
        FROM pending_edits
        ORDER BY created_at ASC
      `,
    );

    return rows
      .map((row) => {
        const patch = parsePendingPatch(row.patchJson);
        if (!patch) {
          return null;
        }
        return {
          id: row.id,
          productId: row.productId,
          patch,
          validationRequestId: row.validationRequestId,
        };
      })
      .filter((row): row is NonNullable<typeof row> => Boolean(row));
  },

  async removePendingEdit(editId: string): Promise<void> {
    const db = await getDatabase();
    await db.runAsync(
      `
        DELETE FROM pending_edits
        WHERE id = ?
      `,
      [editId],
    );
  },

  async markPendingAsRejected(validationRequestId: string): Promise<void> {
    const db = await getDatabase();
    const row = await db.getFirstAsync<{ productId: string }>(
      `
        SELECT product_id AS productId
        FROM pending_edits
        WHERE validation_request_id = ?
        LIMIT 1
      `,
      [validationRequestId],
    );
    if (!row) {
      return;
    }

    await db.withTransactionAsync(async () => {
      await db.runAsync(
        `
          UPDATE products
          SET status_validacao = 'REJEITADO',
              sync_status = 'ERRO',
              local_pending_changes = 0,
              last_pending_validation_id = NULL
          WHERE id = ?
        `,
        [row.productId],
      );
      await db.runAsync(
        `
          DELETE FROM pending_edits
          WHERE validation_request_id = ?
        `,
        [validationRequestId],
      );
    });
  },

  async clearPendingEditByProduct(productId: string): Promise<void> {
    const db = await getDatabase();
    await db.withTransactionAsync(async () => {
      await db.runAsync(
        `
          DELETE FROM pending_edits
          WHERE product_id = ?
        `,
        [productId],
      );
      await db.runAsync(
        `
          UPDATE products
          SET local_pending_changes = 0,
              last_pending_validation_id = NULL
          WHERE id = ?
        `,
        [productId],
      );
    });
  },

  async upsertPendingEdit(
    productId: string,
    patch: ProductUpdateDTO & { quantidadeDelta?: number; motivo?: string },
    validationRequestId?: string | null,
  ): Promise<void> {
    const db = await getDatabase();
    const now = toIsoNow();
    const existing = await db.getFirstAsync<{ id: string }>(
      `
        SELECT id
        FROM pending_edits
        WHERE product_id = ?
        LIMIT 1
      `,
      [productId],
    );

    if (!existing) {
      await db.runAsync(
        `
          INSERT INTO pending_edits (
            id,
            product_id,
            patch_json,
            validation_request_id,
            status,
            created_at,
            updated_at
          ) VALUES (?, ?, ?, ?, 'PENDENTE_APROVACAO', ?, ?)
        `,
        [
          `pending-${productId}-${Date.now()}`,
          productId,
          JSON.stringify(patch),
          validationRequestId ?? null,
          now,
          now,
        ],
      );
      return;
    }

    await db.runAsync(
      `
        UPDATE pending_edits
        SET patch_json = ?,
            validation_request_id = COALESCE(?, validation_request_id),
            status = 'PENDENTE_APROVACAO',
            updated_at = ?
        WHERE id = ?
      `,
      [JSON.stringify(patch), validationRequestId ?? null, now, existing.id],
    );
  },

  async updateProductRow(product: LocalProduct): Promise<void> {
    const db = await getDatabase();
    await db.runAsync(
      `
        UPDATE products
        SET
          referencia = ?,
          codigo_barras = ?,
          imagem_url = ?,
          descricao = ?,
          tamanho = ?,
          cor = ?,
          marca = ?,
          category_id = ?,
          preco_custo = ?,
          preco_venda = ?,
          markup_percentual = ?,
          quantidade_atual = ?,
          quantidade_minima = ?,
          status_ia = ?,
          status_validacao = ?,
          sync_status = ?,
          created_at = ?,
          updated_at = ?,
          local_pending_changes = ?,
          last_pending_validation_id = ?
        WHERE id = ?
      `,
      [
        product.referencia ?? null,
        product.codigoBarras ?? null,
        product.imagemUrl ?? null,
        product.descricao,
        product.tamanho ?? null,
        product.cor ?? null,
        product.marca ?? null,
        product.categoryId ?? null,
        product.precoCusto ?? null,
        product.precoVenda ?? null,
        product.markupPercentual ?? null,
        product.quantidadeAtual ?? 0,
        product.quantidadeMinima ?? 0,
        product.statusIa ?? null,
        product.statusValidacao ?? null,
        product.syncStatus ?? null,
        product.createdAt ?? null,
        product.updatedAt ?? null,
        product.localPendingChanges ?? 0,
        product.lastPendingValidationId ?? null,
        product.id,
      ],
    );
  },

  async setImageUrl(productId: string, imageUrl: string | null): Promise<void> {
    const db = await getDatabase();
    await db.runAsync(
      `
        UPDATE products
        SET imagem_url = ?, updated_at = ?
        WHERE id = ?
      `,
      [imageUrl, toIsoNow(), productId],
    );
  },
};
