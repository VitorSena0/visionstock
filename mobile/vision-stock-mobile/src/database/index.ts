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

  await db.execAsync(`
    PRAGMA journal_mode = WAL;

    CREATE TABLE IF NOT EXISTS sync_queue (
      id TEXT PRIMARY KEY NOT NULL,
      type TEXT NOT NULL,
      payload TEXT NOT NULL,
      created_at TEXT NOT NULL,
      retry_count INTEGER NOT NULL DEFAULT 0
    );
  `);
};
