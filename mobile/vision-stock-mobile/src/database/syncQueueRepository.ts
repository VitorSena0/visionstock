import { getDatabase } from '.';
import type { SyncQueueOperationType } from '../types/product';

type SyncQueueRow = {
  id: string;
  type: SyncQueueOperationType;
  payload: string;
  createdAt: string;
  retryCount: number;
};

const toIsoNow = () => new Date().toISOString();

export const syncQueueRepository = {
  async enqueue(type: SyncQueueOperationType, payload: unknown): Promise<string> {
    const db = await getDatabase();

    if (
      type === 'IMAGE_SET_PRIMARY' &&
      payload &&
      typeof payload === 'object' &&
      'productId' in payload &&
      typeof (payload as { productId?: unknown }).productId === 'string'
    ) {
      const productId = (payload as { productId: string }).productId;
      await db.runAsync(
        `
          DELETE FROM sync_queue
          WHERE type = 'IMAGE_SET_PRIMARY'
            AND payload LIKE ?
        `,
        [`%"productId":"${productId}"%`],
      );
    }

    const id = `queue-${type}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    await db.runAsync(
      `
        INSERT INTO sync_queue (id, type, payload, created_at, retry_count)
        VALUES (?, ?, ?, ?, 0)
      `,
      [id, type, JSON.stringify(payload), toIsoNow()],
    );
    return id;
  },

  async list(): Promise<SyncQueueRow[]> {
    const db = await getDatabase();
    return db.getAllAsync<SyncQueueRow>(
      `
        SELECT
          id,
          type,
          payload,
          created_at AS createdAt,
          retry_count AS retryCount
        FROM sync_queue
        ORDER BY created_at ASC
      `,
    );
  },

  async remove(id: string): Promise<void> {
    const db = await getDatabase();
    await db.runAsync(
      `
        DELETE FROM sync_queue
        WHERE id = ?
      `,
      [id],
    );
  },

  async incrementRetry(id: string): Promise<void> {
    const db = await getDatabase();
    await db.runAsync(
      `
        UPDATE sync_queue
        SET retry_count = retry_count + 1
        WHERE id = ?
      `,
      [id],
    );
  },
};
