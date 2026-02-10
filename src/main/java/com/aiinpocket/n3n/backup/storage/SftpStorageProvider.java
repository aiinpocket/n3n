package com.aiinpocket.n3n.backup.storage;

import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.FingerprintVerifier;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;

import java.io.*;
import java.util.List;

/**
 * SFTP 儲存提供者（使用 SSHJ）
 */
@Slf4j
public class SftpStorageProvider implements CloudStorageProvider {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String privateKey;
    private final String basePath;
    private final String hostKeyFingerprint;

    public SftpStorageProvider(String host, int port, String username, String password,
                               String privateKey, String basePath) {
        this(host, port, username, password, privateKey, basePath, null);
    }

    public SftpStorageProvider(String host, int port, String username, String password,
                               String privateKey, String basePath, String hostKeyFingerprint) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.privateKey = privateKey;
        this.basePath = normalizePath(basePath);
        this.hostKeyFingerprint = hostKeyFingerprint;
    }

    @Override
    public void upload(String filename, byte[] data) throws IOException {
        try (SSHClient ssh = connect()) {
            try (SFTPClient sftp = ssh.newSFTPClient()) {
                String remotePath = basePath + filename;
                ensureDirectoryExists(sftp, basePath);

                try (OutputStream os = sftp.open(remotePath, java.util.EnumSet.of(
                        net.schmizz.sshj.sftp.OpenMode.CREAT,
                        net.schmizz.sshj.sftp.OpenMode.WRITE,
                        net.schmizz.sshj.sftp.OpenMode.TRUNC
                )).new RemoteFileOutputStream()) {
                    os.write(data);
                }
                log.info("Uploaded backup to SFTP: {}", remotePath);
            }
        }
    }

    @Override
    public byte[] download(String filename) throws IOException {
        try (SSHClient ssh = connect()) {
            try (SFTPClient sftp = ssh.newSFTPClient()) {
                String remotePath = basePath + filename;
                try (InputStream is = sftp.open(remotePath).new RemoteFileInputStream();
                     ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    is.transferTo(baos);
                    return baos.toByteArray();
                }
            }
        }
    }

    @Override
    public List<StorageFileInfo> list(String prefix) throws IOException {
        try (SSHClient ssh = connect()) {
            try (SFTPClient sftp = ssh.newSFTPClient()) {
                List<RemoteResourceInfo> files = sftp.ls(basePath);
                String filterPrefix = prefix != null ? prefix : "";

                return files.stream()
                        .filter(f -> f.isRegularFile())
                        .filter(f -> f.getName().startsWith(filterPrefix))
                        .map(f -> new StorageFileInfo(
                                f.getName(),
                                f.getAttributes().getSize(),
                                String.valueOf(f.getAttributes().getMtime())
                        ))
                        .toList();
            }
        }
    }

    @Override
    public void delete(String filename) throws IOException {
        try (SSHClient ssh = connect()) {
            try (SFTPClient sftp = ssh.newSFTPClient()) {
                sftp.rm(basePath + filename);
            }
        }
    }

    @Override
    public boolean testConnection() {
        try (SSHClient ssh = connect()) {
            try (SFTPClient sftp = ssh.newSFTPClient()) {
                sftp.ls(basePath.isEmpty() ? "/" : basePath);
                return true;
            }
        } catch (Exception e) {
            log.warn("SFTP connection test failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getProviderType() {
        return "sftp";
    }

    private SSHClient connect() throws IOException {
        SSHClient ssh = new SSHClient();
        if (hostKeyFingerprint != null && !hostKeyFingerprint.isBlank()) {
            ssh.addHostKeyVerifier(FingerprintVerifier.getInstance(hostKeyFingerprint));
        } else {
            log.warn("SFTP host key verification disabled (no fingerprint configured). " +
                     "Configure a host key fingerprint for production use.");
            ssh.addHostKeyVerifier(new PromiscuousVerifier());
        }
        ssh.connect(host, port);

        if (privateKey != null && !privateKey.isBlank()) {
            KeyProvider keyProvider = ssh.loadKeys(privateKey, null, null);
            ssh.authPublickey(username, keyProvider);
        } else if (password != null && !password.isBlank()) {
            ssh.authPassword(username, password);
        } else {
            throw new IOException("No SFTP authentication method configured");
        }

        return ssh;
    }

    private void ensureDirectoryExists(SFTPClient sftp, String path) {
        if (path == null || path.isBlank() || path.equals("/")) return;
        try {
            sftp.statExistence(path);
        } catch (IOException e) {
            try {
                sftp.mkdirs(path);
            } catch (IOException ex) {
                log.debug("Failed to create directory: {}", path);
            }
        }
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) return "/";
        String p = path.trim();
        if (!p.endsWith("/")) p += "/";
        if (!p.startsWith("/")) p = "/" + p;
        return p;
    }
}
