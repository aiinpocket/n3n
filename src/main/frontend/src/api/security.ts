import apiClient from './client';

export interface SecurityStatus {
  needsRecoveryKeySetup: boolean;
  keyMismatch: boolean;
  currentKeyVersion: number;
}

export interface MigrateResponse {
  success: boolean;
  message: string;
  newRecoveryKey?: {
    words: string[];
    keyHash: string;
    keyVersion: number;
  };
}

export const securityApi = {
  /**
   * 取得加密系統狀態
   */
  getStatus: async (): Promise<SecurityStatus> => {
    const response = await apiClient.get('/security/status');
    return response.data;
  },

  /**
   * 確認已備份 Recovery Key
   */
  confirmRecoveryKeyBackup: async (recoveryKeyPhrase: string): Promise<void> => {
    await apiClient.post('/security/recovery-key/confirm', {
      recoveryKeyPhrase,
    });
  },

  /**
   * 使用舊 Recovery Key 遷移單一憑證
   */
  migrateCredential: async (
    oldRecoveryKeyPhrase: string,
    credentialId: string
  ): Promise<MigrateResponse> => {
    const response = await apiClient.post('/security/migrate', {
      oldRecoveryKeyPhrase,
      credentialId,
    });
    return response.data;
  },

  /**
   * 緊急還原
   */
  emergencyRestore: async (
    recoveryKeyPhrase: string,
    permanentPassword: string
  ): Promise<MigrateResponse> => {
    const response = await apiClient.post('/security/emergency-restore', {
      recoveryKeyPhrase,
      permanentPassword,
    });
    return response.data;
  },
};
