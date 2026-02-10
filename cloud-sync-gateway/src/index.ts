import { HttpFunction } from '@google-cloud/functions-framework';
import {
  getStorage,
  uploadFile,
  downloadFile,
  listFiles,
  softDeleteFile,
  getMaxPayloadSize,
} from './storage';
import type {
  UploadRequest,
  DownloadRequest,
  ListRequest,
  DeleteRequest,
} from './types';

/**
 * 從 Authorization header 提取 fingerprint
 * Bearer token 必須是 64 個 hex 字元（SHA-256 完整輸出）
 */
function extractFingerprint(authHeader: string | undefined): string | null {
  if (!authHeader?.startsWith('Bearer ')) return null;
  const token = authHeader.substring(7).trim();
  if (!/^[0-9a-f]{64}$/i.test(token)) return null;
  return token.toLowerCase();
}

/**
 * N3N Cloud Sync Gateway - Cloud Function 入口
 *
 * 所有用戶共用同一個 GCS bucket，透過 fingerprint 做 namespace 隔離。
 * fingerprint = SHA-256(masterKey)，256-bit 不可暴力破解。
 * GCS 憑證從 Secret Manager 讀取，永不暴露給客戶端。
 */
export const n3nCloudSync: HttpFunction = async (req, res) => {
  // CORS
  res.set('Access-Control-Allow-Origin', '*');
  res.set('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.set('Access-Control-Allow-Headers', 'Authorization, Content-Type');

  if (req.method === 'OPTIONS') {
    res.status(204).send('');
    return;
  }

  // Health check（無需認證）
  if (req.path === '/health' && req.method === 'GET') {
    res.json({ status: 'ok', version: '1.0.0' });
    return;
  }

  // 所有其他端點需要認證
  const fingerprint = extractFingerprint(req.headers.authorization);
  if (!fingerprint) {
    res.status(401).json({ error: 'Invalid or missing Bearer token (64 hex chars required)' });
    return;
  }

  try {
    const storage = await getStorage();

    switch (req.path) {
      case '/upload': {
        const body = req.body as UploadRequest;
        if (!body?.filename || !body?.data) {
          res.status(400).json({ error: 'Missing filename or data' });
          return;
        }

        const buffer = Buffer.from(body.data, 'base64');
        if (buffer.length > getMaxPayloadSize()) {
          res.status(413).json({
            error: 'Payload too large',
            maxSize: `${getMaxPayloadSize() / 1024 / 1024}MB`,
          });
          return;
        }

        await uploadFile(storage, fingerprint, body.filename, buffer);
        res.json({ success: true });
        return;
      }

      case '/download': {
        const body = req.body as DownloadRequest;
        if (!body?.filename) {
          res.status(400).json({ error: 'Missing filename' });
          return;
        }

        try {
          const result = await downloadFile(storage, fingerprint, body.filename);
          res.json({
            data: result.data.toString('base64'),
            size: result.metadata.size,
            lastModified: result.metadata.lastModified,
          });
        } catch (err: unknown) {
          const message = err instanceof Error ? err.message : 'Unknown error';
          if (message === 'File not found') {
            res.status(404).json({ error: 'File not found' });
          } else {
            throw err;
          }
        }
        return;
      }

      case '/list': {
        const body = req.body as ListRequest;
        const prefix = body?.prefix || '';
        const files = await listFiles(storage, fingerprint, prefix);
        res.json({ files });
        return;
      }

      case '/delete': {
        const body = req.body as DeleteRequest;
        if (!body?.filename) {
          res.status(400).json({ error: 'Missing filename' });
          return;
        }

        try {
          await softDeleteFile(storage, fingerprint, body.filename);
          res.json({ success: true, softDeleted: true });
        } catch (err: unknown) {
          const message = err instanceof Error ? err.message : 'Unknown error';
          if (message === 'File not found') {
            res.status(404).json({ error: 'File not found' });
          } else {
            throw err;
          }
        }
        return;
      }

      default:
        res.status(404).json({ error: 'Not found' });
        return;
    }
  } catch (err: unknown) {
    console.error('Request failed:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
};
