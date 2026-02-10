import { Storage } from '@google-cloud/storage';
import { SecretManagerServiceClient } from '@google-cloud/secret-manager';
import type { FileInfo } from './types';

const BUCKET_NAME = process.env.BUCKET_NAME || 'n3n-public';
const SECRET_NAME = process.env.SECRET_NAME ||
  'projects/local-hardware/secrets/n3n-cloud-sync-gcs-key/versions/latest';
const MAX_PAYLOAD_SIZE = 10 * 1024 * 1024; // 10MB

let storageInstance: Storage | null = null;

/**
 * 取得 GCS Storage instance（冷啟動只建立一次）
 * 優先從 Secret Manager 讀取憑證；若未設定或讀取失敗則使用 Application Default Credentials
 */
export async function getStorage(): Promise<Storage> {
  if (storageInstance) return storageInstance;

  if (SECRET_NAME) {
    try {
      const secretClient = new SecretManagerServiceClient();
      const [version] = await secretClient.accessSecretVersion({ name: SECRET_NAME });
      if (version.payload?.data) {
        const credentials = JSON.parse(version.payload.data.toString());
        storageInstance = new Storage({ credentials });
        return storageInstance;
      }
    } catch (err) {
      console.warn('Secret Manager unavailable, falling back to ADC:', err);
    }
  }

  // Fallback: Application Default Credentials（同專案 Cloud Function 自帶權限）
  storageInstance = new Storage();
  return storageInstance;
}

export function getBucketName(): string {
  return BUCKET_NAME;
}

export function getMaxPayloadSize(): number {
  return MAX_PAYLOAD_SIZE;
}

/**
 * 建構 GCS 物件路徑（含 path traversal 防護）
 */
export function buildObjectPath(fingerprint: string, filename: string): string {
  const sanitized = sanitizePath(filename);
  return `data/${fingerprint}/${sanitized}`;
}

/**
 * 建構軟刪除的 trash 路徑
 */
export function buildTrashPath(fingerprint: string, filename: string): string {
  const sanitized = sanitizePath(filename);
  const timestamp = Date.now();
  return `_trash/${fingerprint}/${timestamp}_${sanitized}`;
}

/**
 * Path traversal 防護
 */
function sanitizePath(path: string): string {
  return path
    .replace(/\.\./g, '')
    .replace(/^\/+/, '')
    .replace(/\/\/+/g, '/');
}

/**
 * 上傳檔案到 GCS
 */
export async function uploadFile(
  storage: Storage,
  fingerprint: string,
  filename: string,
  data: Buffer
): Promise<string> {
  if (data.length > MAX_PAYLOAD_SIZE) {
    throw new Error(`Payload too large: ${data.length} bytes (max ${MAX_PAYLOAD_SIZE})`);
  }

  const objectPath = buildObjectPath(fingerprint, filename);
  const bucket = storage.bucket(BUCKET_NAME);
  await bucket.file(objectPath).save(data, {
    contentType: 'application/octet-stream',
    metadata: { cacheControl: 'no-cache' },
  });

  return objectPath;
}

/**
 * 從 GCS 下載檔案
 */
export async function downloadFile(
  storage: Storage,
  fingerprint: string,
  filename: string
): Promise<{ data: Buffer; metadata: { size: number; lastModified: string } }> {
  const objectPath = buildObjectPath(fingerprint, filename);
  const bucket = storage.bucket(BUCKET_NAME);
  const file = bucket.file(objectPath);

  const [exists] = await file.exists();
  if (!exists) {
    throw new Error('File not found');
  }

  const [content] = await file.download();
  const [metadata] = await file.getMetadata();

  return {
    data: content,
    metadata: {
      size: Number(metadata.size || content.length),
      lastModified: metadata.updated || new Date().toISOString(),
    },
  };
}

/**
 * 列出 GCS 中指定前綴的檔案
 */
export async function listFiles(
  storage: Storage,
  fingerprint: string,
  prefix: string
): Promise<FileInfo[]> {
  const fullPrefix = `data/${fingerprint}/${sanitizePath(prefix)}`;
  const bucket = storage.bucket(BUCKET_NAME);

  const [files] = await bucket.getFiles({
    prefix: fullPrefix,
    maxResults: 1000,
  });

  const result: FileInfo[] = [];
  for (const file of files) {
    const [metadata] = await file.getMetadata();
    // 回傳相對於 fingerprint 的路徑
    const relativeName = file.name.replace(`data/${fingerprint}/`, '');
    result.push({
      filename: relativeName,
      size: Number(metadata.size || 0),
      lastModified: metadata.updated || '',
    });
  }

  return result;
}

/**
 * 軟刪除：搬到 _trash/ 而非直接刪除
 */
export async function softDeleteFile(
  storage: Storage,
  fingerprint: string,
  filename: string
): Promise<void> {
  const sourcePath = buildObjectPath(fingerprint, filename);
  const trashPath = buildTrashPath(fingerprint, filename);
  const bucket = storage.bucket(BUCKET_NAME);

  const sourceFile = bucket.file(sourcePath);
  const [exists] = await sourceFile.exists();
  if (!exists) {
    throw new Error('File not found');
  }

  // 複製到 trash → 刪除原始
  await sourceFile.copy(bucket.file(trashPath));
  await sourceFile.delete();
}
