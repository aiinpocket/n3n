package com.aiinpocket.n3n.backup.storage;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * S3 / R2 (S3 相容) 儲存提供者
 */
@Slf4j
public class S3StorageProvider implements CloudStorageProvider {

    private final S3Client s3Client;
    private final String bucket;
    private final String basePath;
    private final boolean isR2;

    public S3StorageProvider(String endpoint, String region, String accessKey, String secretKey,
                             String bucket, String basePath, boolean isR2) {
        this.bucket = bucket;
        this.basePath = normalizePath(basePath);
        this.isR2 = isR2;

        S3ClientBuilder builder = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)
                ))
                .region(Region.of(region != null ? region : "us-east-1"));

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        // R2 needs path-style access
        if (isR2) {
            builder.forcePathStyle(true);
        }

        this.s3Client = builder.build();
    }

    @Override
    public void upload(String filename, byte[] data) throws IOException {
        try {
            String key = basePath + filename;
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType("application/octet-stream")
                            .build(),
                    RequestBody.fromBytes(data)
            );
            log.info("Uploaded backup to {}: {}", getProviderType(), key);
        } catch (S3Exception e) {
            throw new IOException("Failed to upload to " + getProviderType() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] download(String filename) throws IOException {
        try {
            String key = basePath + filename;
            var response = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build()
            );
            return response.asByteArray();
        } catch (S3Exception e) {
            throw new IOException("Failed to download from " + getProviderType() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public List<StorageFileInfo> list(String prefix) throws IOException {
        try {
            String fullPrefix = basePath + (prefix != null ? prefix : "");
            var response = s3Client.listObjectsV2(
                    ListObjectsV2Request.builder()
                            .bucket(bucket)
                            .prefix(fullPrefix)
                            .maxKeys(1000)
                            .build()
            );
            return response.contents().stream()
                    .filter(obj -> obj.key().startsWith(basePath))
                    .map(obj -> new StorageFileInfo(
                            obj.key().substring(basePath.length()),
                            obj.size(),
                            obj.lastModified() != null ? obj.lastModified().toString() : ""
                    ))
                    .toList();
        } catch (S3Exception e) {
            throw new IOException("Failed to list from " + getProviderType() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String filename) throws IOException {
        try {
            String key = basePath + filename;
            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build()
            );
        } catch (S3Exception e) {
            throw new IOException("Failed to delete from " + getProviderType() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public boolean testConnection() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return true;
        } catch (Exception e) {
            log.warn("{} connection test failed: {}", getProviderType(), e.getMessage());
            return false;
        }
    }

    @Override
    public String getProviderType() {
        return isR2 ? "r2" : "s3";
    }

    @Override
    public void close() {
        s3Client.close();
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) return "";
        String p = path.trim();
        if (!p.endsWith("/")) p += "/";
        if (p.startsWith("/")) p = p.substring(1);
        return p;
    }
}
