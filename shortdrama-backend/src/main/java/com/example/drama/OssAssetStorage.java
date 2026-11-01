package com.example.drama;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.exceptions.ServiceException;
import com.aliyun.sdk.service.oss2.models.CopyObjectRequest;
import com.aliyun.sdk.service.oss2.models.DeleteObjectRequest;
import com.aliyun.sdk.service.oss2.models.GetObjectRequest;
import com.aliyun.sdk.service.oss2.models.GetObjectResult;
import com.aliyun.sdk.service.oss2.models.HeadObjectRequest;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class OssAssetStorage {
    static final String PREFIX = "ai-short-drama/assets/";
    private static final Pattern OBJECT_KEY = Pattern.compile(
            "^ai-short-drama/assets/[0-9a-f-]{36}\\.(jpg|png|webp|mp4|webm)$");
    private static final Pattern QUARANTINE_KEY = Pattern.compile(
            "^ai-short-drama/assets/\\.delete/[0-9a-f-]{36}-[0-9a-f-]{36}\\.(jpg|png|webp|mp4|webm)$");

    private final OSSClient client;
    private final OssProperties properties;

    public OssAssetStorage(ObjectProvider<OSSClient> clients, OssProperties properties) {
        this.client = clients.getIfAvailable();
        this.properties = properties;
    }

    boolean enabled() { return client != null; }

    String objectKey(String storageFilename) {
        String key = PREFIX + storageFilename;
        requireObjectKey(key);
        return key;
    }

    void putFile(String key, Path file, String contentType, long size) throws IOException {
        requireObjectKey(key);
        try (InputStream input = Files.newInputStream(file)) {
            requireClient().putObject(PutObjectRequest.newBuilder()
                    .bucket(properties.getBucketName()).key(key).contentType(contentType)
                    .objectAcl("private").forbidOverwrite(true)
                    .body(BinaryData.fromStream(input, size)).build());
        }
    }

    long size(String key) {
        requireObjectKey(key);
        Long size = requireClient().headObject(HeadObjectRequest.newBuilder()
                .bucket(properties.getBucketName()).key(key).build()).contentLength();
        if (size == null || size < 0) throw new IllegalStateException("OSS 素材大小无效");
        return size;
    }

    InputStream openRange(String key, long start, long count) throws IOException {
        requireObjectKey(key);
        if (start < 0 || count <= 0 || start > Long.MAX_VALUE - count) {
            throw new IllegalArgumentException("非法 OSS Range");
        }
        long end = start + count - 1;
        GetObjectResult result = requireClient().getObject(GetObjectRequest.newBuilder()
                .bucket(properties.getBucketName()).key(key)
                .range("bytes=" + start + "-" + end).build());
        if (result.contentLength() != null && result.contentLength() != count) {
            closeResult(result);
            throw new IOException("OSS Range 响应长度不匹配");
        }
        InputStream body = result.body();
        if (body == null) {
            closeResult(result);
            throw new IOException("OSS 未返回素材流");
        }
        return new FilterInputStream(body) {
            private long remaining = count;

            @Override
            public int read() throws IOException {
                if (remaining == 0) return -1;
                int value = super.read();
                if (value >= 0) remaining--;
                return value;
            }

            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException {
                if (remaining == 0) return -1;
                int read = super.read(bytes, offset, (int) Math.min(length, remaining));
                if (read > 0) remaining -= read;
                return read;
            }

            @Override
            public void close() throws IOException {
                if (remaining > 0) result.abort();
                try {
                    result.close();
                } catch (Exception exception) {
                    throw new IOException("关闭 OSS 素材流失败", exception);
                }
            }
        };
    }

    Quarantine quarantine(String key) {
        requireObjectKey(key);
        String filename = key.substring(PREFIX.length());
        String pending = PREFIX + ".delete/" + UUID.randomUUID() + "-" + filename;
        requireQuarantineKey(pending);
        copy(key, pending);
        try {
            deleteObject(key, false);
            return new Quarantine(key, pending);
        } catch (RuntimeException exception) {
            try {
                copy(pending, key);
                deleteObject(pending, true);
            } catch (RuntimeException restoreFailure) {
                exception.addSuppressed(restoreFailure);
            }
            throw exception;
        }
    }

    void restore(Quarantine quarantine) {
        requireObjectKey(quarantine.objectKey());
        requireQuarantineKey(quarantine.pendingKey());
        copy(quarantine.pendingKey(), quarantine.objectKey());
        deleteObject(quarantine.pendingKey(), true);
    }

    void purge(Quarantine quarantine) {
        requireObjectKey(quarantine.objectKey());
        deleteObject(quarantine.pendingKey(), true);
    }

    void deleteUploadedObject(String key) {
        deleteObject(key, false);
    }

    boolean isNotFound(RuntimeException exception) {
        return exception instanceof ServiceException service && service.statusCode() == 404;
    }

    private void copy(String source, String target) {
        if (OBJECT_KEY.matcher(source).matches()) requireObjectKey(source);
        else requireQuarantineKey(source);
        if (OBJECT_KEY.matcher(target).matches()) requireObjectKey(target);
        else requireQuarantineKey(target);
        requireClient().copyObject(CopyObjectRequest.newBuilder()
                .bucket(properties.getBucketName()).key(target)
                .sourceBucket(properties.getBucketName()).sourceKey(source)
                .objectAcl("private").build());
    }

    private void deleteObject(String key, boolean quarantine) {
        if (quarantine) requireQuarantineKey(key);
        else requireObjectKey(key);
        requireClient().deleteObject(DeleteObjectRequest.newBuilder()
                .bucket(properties.getBucketName()).key(key).build());
    }

    private OSSClient requireClient() {
        if (client == null) throw new IllegalStateException("OSS 未配置");
        return client;
    }

    private void requireObjectKey(String key) {
        if (key == null || !OBJECT_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("非法 OSS 素材 key");
        }
    }

    private void requireQuarantineKey(String key) {
        if (key == null || !QUARANTINE_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("非法 OSS 待删除 key");
        }
    }

    private void closeResult(GetObjectResult result) throws IOException {
        try {
            result.close();
        } catch (Exception exception) {
            throw new IOException("关闭 OSS 响应失败", exception);
        }
    }

    record Quarantine(String objectKey, String pendingKey) {}
}
