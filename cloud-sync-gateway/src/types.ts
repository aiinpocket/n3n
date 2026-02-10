export interface UploadRequest {
  filename: string;
  data: string; // base64 encoded
}

export interface DownloadRequest {
  filename: string;
}

export interface ListRequest {
  prefix: string;
}

export interface DeleteRequest {
  filename: string;
}

export interface FileInfo {
  filename: string;
  size: number;
  lastModified: string;
}

export interface ListResponse {
  files: FileInfo[];
}

export interface DownloadResponse {
  data: string; // base64 encoded
  size: number;
  lastModified: string;
}

export interface ErrorResponse {
  error: string;
}
