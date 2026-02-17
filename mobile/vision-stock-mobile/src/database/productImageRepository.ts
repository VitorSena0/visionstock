import { getDatabase } from '.';
import type { LocalProductImage, ProductImageMetadataDTO } from '../types/product';

type ProductImageRow = {
  id: string;
  productId: string;
  remoteImageId: string | null;
  localUri: string | null;
  cachedUri: string | null;
  cacheStatus: 'NONE' | 'SYNCED' | 'ERROR' | null;
  cacheUpdatedAt: string | null;
  cacheError: string | null;
  mimeType: string | null;
  fileSize: number | null;
  width: number | null;
  height: number | null;
  sha256: string | null;
  isPrimary: number;
  sourceType: 'SCAN' | 'MANUAL' | 'POST_EDIT';
  syncStatus: 'PENDENTE' | 'SYNCED' | 'ERRO';
  createdAt: string;
  updatedAt: string;
  deletedAt: string | null;
};

const toIsoNow = () => new Date().toISOString();

const mapRow = (row: ProductImageRow): LocalProductImage => ({
  id: row.id,
  productId: row.productId,
  remoteImageId: row.remoteImageId,
  localUri: row.localUri,
  cachedUri: row.cachedUri,
  cacheStatus: row.cacheStatus,
  cacheUpdatedAt: row.cacheUpdatedAt,
  cacheError: row.cacheError,
  mimeType: row.mimeType,
  fileSize: row.fileSize,
  width: row.width,
  height: row.height,
  sha256: row.sha256,
  isPrimary: row.isPrimary === 1,
  sourceType: row.sourceType,
  syncStatus: row.syncStatus,
  createdAt: row.createdAt,
  updatedAt: row.updatedAt,
  deletedAt: row.deletedAt,
});

const normalizeBool = (value?: boolean | null) => (value ? 1 : 0);

export const productImageRepository = {
  async getByProductId(productId: string): Promise<LocalProductImage[]> {
    const db = await getDatabase();
    const rows = await db.getAllAsync<ProductImageRow>(
      `
        SELECT
          id,
          product_id AS productId,
          remote_image_id AS remoteImageId,
          local_uri AS localUri,
          cached_uri AS cachedUri,
          cache_status AS cacheStatus,
          cache_updated_at AS cacheUpdatedAt,
          cache_error AS cacheError,
          mime_type AS mimeType,
          file_size AS fileSize,
          width,
          height,
          sha256,
          is_primary AS isPrimary,
          source_type AS sourceType,
          sync_status AS syncStatus,
          created_at AS createdAt,
          updated_at AS updatedAt,
          deleted_at AS deletedAt
        FROM product_images
        WHERE product_id = ? AND deleted_at IS NULL
        ORDER BY is_primary DESC, created_at ASC
      `,
      [productId],
    );
    return rows.map(mapRow);
  },

  async saveDraftImage(params: {
    id?: string;
    productId: string;
    localUri: string;
    mimeType?: string | null;
    fileSize?: number | null;
    width?: number | null;
    height?: number | null;
    isPrimary?: boolean;
    sourceType: 'SCAN' | 'MANUAL' | 'POST_EDIT';
    syncStatus?: 'PENDENTE' | 'SYNCED' | 'ERRO';
    remoteImageId?: string | null;
  }): Promise<string> {
    const db = await getDatabase();
    const now = toIsoNow();
    const id = params.id ?? `img-${params.productId}-${Date.now()}`;

    if (params.isPrimary) {
      await db.runAsync(
        `
          UPDATE product_images
          SET is_primary = 0, updated_at = ?
          WHERE product_id = ? AND deleted_at IS NULL
        `,
        [now, params.productId],
      );
    }

    await db.runAsync(
      `
        INSERT INTO product_images (
          id,
          product_id,
          remote_image_id,
          local_uri,
          cached_uri,
          cache_status,
          cache_updated_at,
          cache_error,
          mime_type,
          file_size,
          width,
          height,
          sha256,
          is_primary,
          source_type,
          sync_status,
          created_at,
          updated_at,
          deleted_at
        ) VALUES (?, ?, ?, ?, NULL, 'NONE', NULL, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)
        ON CONFLICT(id) DO UPDATE SET
          product_id = excluded.product_id,
          remote_image_id = excluded.remote_image_id,
          local_uri = excluded.local_uri,
          cached_uri = excluded.cached_uri,
          cache_status = excluded.cache_status,
          cache_updated_at = excluded.cache_updated_at,
          cache_error = excluded.cache_error,
          mime_type = excluded.mime_type,
          file_size = excluded.file_size,
          width = excluded.width,
          height = excluded.height,
          sha256 = excluded.sha256,
          is_primary = excluded.is_primary,
          source_type = excluded.source_type,
          sync_status = excluded.sync_status,
          updated_at = excluded.updated_at,
          deleted_at = NULL
      `,
      [
        id,
        params.productId,
        params.remoteImageId ?? null,
        params.localUri,
        params.mimeType ?? null,
        params.fileSize ?? null,
        params.width ?? null,
        params.height ?? null,
        null,
        normalizeBool(params.isPrimary),
        params.sourceType,
        params.syncStatus ?? 'PENDENTE',
        now,
        now,
      ],
    );

    return id;
  },

  async upsertRemoteMetadata(
    productId: string,
    images: ProductImageMetadataDTO[],
  ): Promise<void> {
    const db = await getDatabase();
    const now = toIsoNow();
    const remoteIds = images
      .map((image) => image.id?.trim())
      .filter((id): id is string => Boolean(id));

    await db.withTransactionAsync(async () => {
      for (const image of images) {
        const remoteId = image.id?.trim();
        if (!remoteId) {
          continue;
        }

        const pendingDelete = await db.getFirstAsync<{ id: string }>(
          `
            SELECT id
            FROM product_images
            WHERE product_id = ?
              AND remote_image_id = ?
              AND deleted_at IS NOT NULL
              AND sync_status = 'PENDENTE'
            LIMIT 1
          `,
          [productId, remoteId],
        );
        if (pendingDelete) {
          continue;
        }

        const existing = await db.getFirstAsync<{
          id: string;
          localUri: string | null;
          cachedUri: string | null;
          cacheStatus: 'NONE' | 'SYNCED' | 'ERROR' | null;
          cacheUpdatedAt: string | null;
          cacheError: string | null;
          sourceType: 'SCAN' | 'MANUAL' | 'POST_EDIT' | null;
          syncStatus: 'PENDENTE' | 'SYNCED' | 'ERRO' | null;
        }>(
          `
            SELECT
              id,
              local_uri AS localUri,
              cached_uri AS cachedUri,
              cache_status AS cacheStatus,
              cache_updated_at AS cacheUpdatedAt,
              cache_error AS cacheError,
              source_type AS sourceType,
              sync_status AS syncStatus
            FROM product_images
            WHERE product_id = ?
              AND remote_image_id = ?
              AND deleted_at IS NULL
            ORDER BY
              CASE WHEN id LIKE 'remote-%' THEN 1 ELSE 0 END ASC,
              updated_at DESC
            LIMIT 1
          `,
          [productId, remoteId],
        );

        const localId = existing?.id ?? `remote-${remoteId}`;
        const hasExistingLocalFile =
          Boolean(existing?.localUri && existing.localUri.trim().length > 0) &&
          !String(existing?.localUri).startsWith('http');
        const shouldPreserveLocalFile =
          hasExistingLocalFile &&
          (existing?.syncStatus === 'PENDENTE' || existing?.syncStatus === 'ERRO');
        const preferredLocalUri =
          shouldPreserveLocalFile && existing?.localUri ? existing.localUri : null;
        const sourceType = existing?.sourceType ?? 'POST_EDIT';

        await db.runAsync(
          `
            INSERT INTO product_images (
              id,
              product_id,
              remote_image_id,
              local_uri,
              cached_uri,
              cache_status,
              cache_updated_at,
              cache_error,
              mime_type,
              file_size,
              width,
              height,
              sha256,
              is_primary,
              source_type,
              sync_status,
              created_at,
              updated_at,
              deleted_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)
            ON CONFLICT(id) DO UPDATE SET
              remote_image_id = excluded.remote_image_id,
              local_uri = CASE
                WHEN product_images.sync_status IN ('PENDENTE', 'ERRO')
                  AND product_images.local_uri IS NOT NULL
                  AND product_images.local_uri <> ''
                  AND product_images.local_uri NOT LIKE 'http%'
                THEN product_images.local_uri
                ELSE excluded.local_uri
              END,
              cached_uri = COALESCE(excluded.cached_uri, product_images.cached_uri),
              cache_status = COALESCE(excluded.cache_status, product_images.cache_status),
              cache_updated_at = COALESCE(excluded.cache_updated_at, product_images.cache_updated_at),
              cache_error = CASE
                WHEN COALESCE(excluded.cache_status, product_images.cache_status) = 'ERROR'
                  THEN COALESCE(excluded.cache_error, product_images.cache_error)
                ELSE NULL
              END,
              mime_type = excluded.mime_type,
              file_size = excluded.file_size,
              width = excluded.width,
              height = excluded.height,
              sha256 = excluded.sha256,
              is_primary = excluded.is_primary,
              source_type = excluded.source_type,
              sync_status = 'SYNCED',
              updated_at = excluded.updated_at,
              deleted_at = NULL
          `,
          [
            localId,
            productId,
            remoteId,
            preferredLocalUri,
            existing?.cachedUri ?? null,
            existing?.cacheStatus ?? 'NONE',
            existing?.cacheUpdatedAt ?? null,
            existing?.cacheError ?? null,
            image.contentType ?? null,
            image.fileSize ?? null,
            image.width ?? null,
            image.height ?? null,
            image.sha256 ?? null,
            normalizeBool(image.isPrimary ?? false),
            sourceType,
            'SYNCED',
            image.createdAt ?? now,
            image.updatedAt ?? now,
          ],
        );

        // Remove duplicate entries that reference the same remote image ID.
        await db.runAsync(
          `
            DELETE FROM product_images
            WHERE product_id = ?
              AND remote_image_id = ?
              AND id <> ?
              AND deleted_at IS NULL
          `,
          [productId, remoteId, localId],
        );
      }

      if (remoteIds.length > 0) {
        const placeholders = remoteIds.map(() => '?').join(', ');
        await db.runAsync(
          `
            DELETE FROM product_images
            WHERE product_id = ?
              AND id LIKE 'remote-%'
              AND sync_status = 'SYNCED'
              AND deleted_at IS NULL
              AND remote_image_id IS NOT NULL
              AND remote_image_id NOT IN (${placeholders})
          `,
          [productId, ...remoteIds],
        );
      } else {
        await db.runAsync(
          `
            DELETE FROM product_images
            WHERE product_id = ?
              AND id LIKE 'remote-%'
              AND sync_status = 'SYNCED'
              AND deleted_at IS NULL
          `,
          [productId],
        );
      }

      const activeRows = await db.getAllAsync<{
        id: string;
        isPrimary: number;
        localUri: string | null;
        cachedUri: string | null;
        syncStatus: 'PENDENTE' | 'SYNCED' | 'ERRO';
      }>(
        `
          SELECT
            id,
            is_primary AS isPrimary,
            local_uri AS localUri,
            cached_uri AS cachedUri,
            sync_status AS syncStatus
          FROM product_images
          WHERE product_id = ?
            AND deleted_at IS NULL
          ORDER BY updated_at DESC
        `,
        [productId],
      );

      if (activeRows.length === 0) {
        return;
      }

      const withImageSource = activeRows.filter(
        (row) =>
          (row.cachedUri !== null && row.cachedUri.trim().length > 0) ||
          (row.localUri !== null && row.localUri.trim().length > 0),
      );
      const pendingPrimaryCandidate = withImageSource.find(
        (row) => row.isPrimary === 1 && row.syncStatus === 'PENDENTE',
      );

      let preservePendingPrimary = false;
      if (pendingPrimaryCandidate) {
        const productFilter = `%"productId":"${productId}"%`;
        const localImageFilter = `%"localImageId":"${pendingPrimaryCandidate.id}"%`;
        const pendingPrimaryIntent = await db.getFirstAsync<{ id: string }>(
          `
            SELECT id
            FROM sync_queue
            WHERE (
              type = 'IMAGE_SET_PRIMARY'
              AND payload LIKE ?
              AND payload LIKE ?
            )
            OR (
              type = 'IMAGE_ADD'
              AND payload LIKE ?
              AND payload LIKE ?
              AND payload LIKE '%"isPrimary":true%'
            )
            ORDER BY created_at DESC
            LIMIT 1
          `,
          [
            productFilter,
            localImageFilter,
            productFilter,
            localImageFilter,
          ],
        );
        preservePendingPrimary = Boolean(pendingPrimaryIntent);
      }

      const syncedPrimary = withImageSource.find(
        (row) => row.isPrimary === 1 && row.syncStatus === 'SYNCED',
      );
      const pendingPrimary = preservePendingPrimary ? pendingPrimaryCandidate ?? null : null;
      const selectedPrimary =
        pendingPrimary ??
        syncedPrimary ??
        withImageSource.find((row) => row.isPrimary === 1) ??
        activeRows.find((row) => row.isPrimary === 1) ??
        withImageSource[0] ??
        activeRows[0];

      await db.runAsync(
        `
          UPDATE product_images
          SET
            is_primary = CASE WHEN id = ? THEN 1 ELSE 0 END,
            updated_at = ?
          WHERE product_id = ?
            AND deleted_at IS NULL
        `,
        [selectedPrimary.id, now, productId],
      );
    });
  },

  async setPrimary(productId: string, localImageId: string): Promise<void> {
    const db = await getDatabase();
    const now = toIsoNow();
    await db.withTransactionAsync(async () => {
      await db.runAsync(
        `
          UPDATE product_images
          SET is_primary = 0, updated_at = ?
          WHERE product_id = ? AND deleted_at IS NULL
        `,
        [now, productId],
      );
      await db.runAsync(
        `
          UPDATE product_images
          SET is_primary = 1, updated_at = ?
          WHERE id = ? AND product_id = ? AND deleted_at IS NULL
        `,
        [now, localImageId, productId],
      );
    });
  },

  async markDeleted(localImageId: string): Promise<void> {
    const db = await getDatabase();
    await db.runAsync(
      `
        UPDATE product_images
        SET deleted_at = ?, sync_status = 'PENDENTE'
        WHERE id = ?
      `,
      [toIsoNow(), localImageId],
    );
  },

  async getById(localImageId: string): Promise<LocalProductImage | null> {
    const db = await getDatabase();
    const row = await db.getFirstAsync<ProductImageRow>(
      `
        SELECT
          id,
          product_id AS productId,
          remote_image_id AS remoteImageId,
          local_uri AS localUri,
          cached_uri AS cachedUri,
          cache_status AS cacheStatus,
          cache_updated_at AS cacheUpdatedAt,
          cache_error AS cacheError,
          mime_type AS mimeType,
          file_size AS fileSize,
          width,
          height,
          sha256,
          is_primary AS isPrimary,
          source_type AS sourceType,
          sync_status AS syncStatus,
          created_at AS createdAt,
          updated_at AS updatedAt,
          deleted_at AS deletedAt
        FROM product_images
        WHERE id = ?
        LIMIT 1
      `,
      [localImageId],
    );
    return row ? mapRow(row) : null;
  },

  async markSynced(
    localImageId: string,
    remoteImageId?: string | null,
    remoteUri?: string | null,
  ): Promise<void> {
    const db = await getDatabase();
    await db.runAsync(
      `
        UPDATE product_images
        SET sync_status = 'SYNCED',
            remote_image_id = COALESCE(?, remote_image_id),
            local_uri = CASE
              WHEN local_uri IS NULL OR local_uri = ''
              THEN COALESCE(NULLIF(?, ''), local_uri)
              ELSE local_uri
            END,
            updated_at = ?
        WHERE id = ?
      `,
      [remoteImageId ?? null, remoteUri ?? '', toIsoNow(), localImageId],
    );
  },

  async updateCache(
    localImageId: string,
    params: {
      cachedUri?: string | null;
      status: 'NONE' | 'SYNCED' | 'ERROR';
      error?: string | null;
    },
  ): Promise<void> {
    const db = await getDatabase();
    await db.runAsync(
      `
        UPDATE product_images
        SET cached_uri = ?,
            cache_status = ?,
            cache_updated_at = ?,
            cache_error = ?,
            updated_at = ?
        WHERE id = ?
      `,
      [
        params.cachedUri ?? null,
        params.status,
        toIsoNow(),
        params.error ?? null,
        toIsoNow(),
        localImageId,
      ],
    );
  },

  async markCacheError(localImageId: string, error: string): Promise<void> {
    await this.updateCache(localImageId, {
      cachedUri: null,
      status: 'ERROR',
      error,
    });
  },

  async markPending(localImageId: string): Promise<void> {
    const db = await getDatabase();
    await db.runAsync(
      `
        UPDATE product_images
        SET sync_status = 'PENDENTE',
            updated_at = ?
        WHERE id = ?
      `,
      [toIsoNow(), localImageId],
    );
  },

  async markError(localImageId: string): Promise<void> {
    const db = await getDatabase();
    await db.runAsync(
      `
        UPDATE product_images
        SET sync_status = 'ERRO',
            updated_at = ?
        WHERE id = ?
      `,
      [toIsoNow(), localImageId],
    );
  },

  async deleteById(localImageId: string): Promise<void> {
    const db = await getDatabase();
    await db.runAsync(
      `
        DELETE FROM product_images
        WHERE id = ?
      `,
      [localImageId],
    );
  },
};
