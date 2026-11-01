package com.example.drama;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AssetStorageService {
    private static final Logger log = LoggerFactory.getLogger(AssetStorageService.class);
    private static final Pattern ASSET_REFERENCE = Pattern.compile("^asset:([1-9][0-9]*)$");
    private static final Pattern STORAGE_FILENAME =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp|mp4|webm)$");
    private static final Duration FFPROBE_TIMEOUT = Duration.ofSeconds(20);

    private final MediaAssetRepository assets;
    private final OssAssetStorage oss;
    private final Path root;
    private final String ffprobeExecutable;
    private final long coverMaxBytes;
    private final long videoMaxBytes;

    public AssetStorageService(MediaAssetRepository assets, OssAssetStorage oss,
                               @Value("${app.asset-root:./media/assets}") String assetRoot,
                               @Value("${app.ffprobe-executable:ffprobe}") String ffprobeExecutable,
                               @Value("${app.asset.cover-max-bytes:10485760}") long coverMaxBytes,
                               @Value("${app.asset.video-max-bytes:524288000}") long videoMaxBytes) {
        this.assets = assets;
        this.oss = oss;
        this.root = Path.of(assetRoot).toAbsolutePath().normalize();
        this.ffprobeExecutable = ffprobeExecutable;
        this.coverMaxBytes = coverMaxBytes;
        this.videoMaxBytes = videoMaxBytes;
        if (coverMaxBytes <= 0 || videoMaxBytes <= 0) {
            throw new IllegalArgumentException("素材容量限制必须大于 0");
        }
        ensureRoot();
    }

    StoredUpload store(MultipartFile upload, AssetType requestedType) {
        if (upload == null || upload.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择非空素材文件");
        }
        long limit = requestedType == AssetType.COVER ? coverMaxBytes : videoMaxBytes;
        if (upload.getSize() > limit) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "素材文件超过容量限制");
        }

        Path temporary = root.resolve(".upload-" + UUID.randomUUID() + ".tmp").normalize();
        try {
            copyWithLimit(upload, temporary, limit);
            DetectedFile detected = detect(temporary);
            if (detected.type() != requestedType) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "文件内容与素材类型不一致");
            }
            verifyDeclaredContentType(upload.getContentType(), detected.contentType());
            Integer duration = requestedType == AssetType.VIDEO ? probeDurationSeconds(temporary) : null;
            String storageFilename = UUID.randomUUID() + detected.extension();
            long size = Files.size(temporary);
            if (oss.enabled()) {
                String objectKey = oss.objectKey(storageFilename);
                oss.putFile(objectKey, temporary, detected.contentType(), size);
                deleteTemporary(temporary);
                return new StoredUpload(AssetStorageBackend.OSS, storageFilename, objectKey,
                        detected.contentType(), size, duration);
            }
            Path destination = safeStoragePath(storageFilename);
            moveAtomically(temporary, destination);
            return new StoredUpload(AssetStorageBackend.LOCAL, storageFilename, null,
                    detected.contentType(), Files.size(destination), duration);
        } catch (ResponseStatusException exception) {
            deleteTemporary(temporary);
            throw exception;
        } catch (IOException exception) {
            deleteTemporary(temporary);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "素材文件保存失败", exception);
        } catch (RuntimeException exception) {
            deleteTemporary(temporary);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OSS 素材上传失败", exception);
        }
    }

    public Optional<StoredAssetResource> openVideoReference(String reference) {
        if (reference == null) return Optional.empty();
        Matcher matcher = ASSET_REFERENCE.matcher(reference);
        if (!matcher.matches()) return Optional.empty();
        long id;
        try {
            id = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
        return assets.findById(id)
                .filter(asset -> asset.getType() == AssetType.VIDEO)
                .flatMap(this::open);
    }

    Optional<StoredAssetResource> open(MediaAsset asset) {
        if (asset.getStorageBackend() == AssetStorageBackend.OSS) return openOss(asset);
        try {
            Path path = safeStoragePath(asset.getStorageFilename());
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
            long actualSize = Files.size(path);
            if (actualSize != asset.getSizeBytes()) return Optional.empty();
            return Optional.of(StoredAssetResource.local(path, asset.getContentType(), actualSize));
        } catch (IOException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private Optional<StoredAssetResource> openOss(MediaAsset asset) {
        try {
            String key = asset.getObjectKey();
            long actualSize = oss.size(key);
            if (actualSize != asset.getSizeBytes()) return Optional.empty();
            return Optional.of(new StoredAssetResource("OSS object " + key, asset.getContentType(), actualSize,
                    (start, count) -> oss.openRange(key, start, count)));
        } catch (RuntimeException exception) {
            if (oss.isNotFound(exception)) return Optional.empty();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OSS 素材读取失败", exception);
        }
    }

    QuarantinedAsset quarantine(MediaAsset asset) {
        if (asset.getStorageBackend() == AssetStorageBackend.OSS) {
            try {
                OssAssetStorage.Quarantine quarantined = oss.quarantine(asset.getObjectKey());
                return new QuarantinedAsset(AssetStorageBackend.OSS, asset.getStorageFilename(),
                        asset.getObjectKey(), null, quarantined);
            } catch (RuntimeException exception) {
                if (oss.isNotFound(exception)) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "素材文件不存在");
                }
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OSS 素材无法进入待删除状态", exception);
            }
        }
        String storageFilename = asset.getStorageFilename();
        try {
            Path original = safeStoragePath(storageFilename);
            if (!Files.isRegularFile(original, LinkOption.NOFOLLOW_LINKS)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "素材文件不存在");
            }
            Path pending = root.resolve(".delete-" + storageFilename).normalize();
            if (!pending.getParent().equals(root)) {
                throw new IllegalArgumentException("非法待删除素材路径");
            }
            moveAtomically(original, pending);
            return new QuarantinedAsset(AssetStorageBackend.LOCAL, storageFilename,
                    null, pending, null);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "素材文件无法进入待删除状态", exception);
        }
    }

    void restore(QuarantinedAsset quarantined) {
        if (quarantined.backend() == AssetStorageBackend.OSS) {
            try {
                oss.restore(quarantined.ossQuarantine());
                return;
            } catch (RuntimeException exception) {
                throw new IllegalStateException("数据库删除失败后无法恢复 OSS 素材", exception);
            }
        }
        try {
            Path original = safeStoragePath(quarantined.storageFilename());
            if (Files.exists(original, LinkOption.NOFOLLOW_LINKS)) {
                Files.deleteIfExists(quarantined.pendingPath());
            } else if (Files.exists(quarantined.pendingPath(), LinkOption.NOFOLLOW_LINKS)) {
                moveAtomically(quarantined.pendingPath(), original);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("数据库删除失败后无法恢复素材文件", exception);
        }
    }

    void purge(QuarantinedAsset quarantined) {
        if (quarantined.backend() == AssetStorageBackend.OSS) {
            try {
                oss.purge(quarantined.ossQuarantine());
                return;
            } catch (RuntimeException exception) {
                throw new IllegalStateException("OSS 素材元数据已删除，待删除对象清理失败", exception);
            }
        }
        try {
            Files.deleteIfExists(quarantined.pendingPath());
        } catch (IOException exception) {
            throw new UncheckedIOException("素材元数据已删除，请按精确路径人工清理隔离文件: "
                    + quarantined.pendingPath(), exception);
        }
    }

    void deleteFailedUpload(StoredUpload stored) {
        if (stored.backend() == AssetStorageBackend.OSS) {
            try {
                oss.deleteUploadedObject(stored.objectKey());
                return;
            } catch (RuntimeException exception) {
                log.error("上传元数据保存失败，OSS 素材清理失败: {}", stored.objectKey(), exception);
                throw new IllegalStateException("上传失败后的 OSS 素材清理失败", exception);
            }
        }
        try {
            Files.deleteIfExists(safeStoragePath(stored.storageFilename()));
        } catch (IOException | IllegalArgumentException exception) {
            log.error("上传元数据保存失败，本地素材清理失败: {}", stored.storageFilename(),
                    exception);
            throw new IllegalStateException("上传失败后的素材文件清理失败", exception);
        }
    }

    private void copyWithLimit(MultipartFile upload, Path target, long limit) throws IOException {
        long written = 0;
        byte[] buffer = new byte[8192];
        try (InputStream input = upload.getInputStream();
             OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW,
                     StandardOpenOption.WRITE)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                written += count;
                if (written > limit) {
                    throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                            "素材文件超过容量限制");
                }
                output.write(buffer, 0, count);
            }
        }
    }

    private DetectedFile detect(Path path) throws IOException {
        byte[] header = new byte[16];
        int count;
        try (InputStream input = Files.newInputStream(path)) {
            count = input.read(header);
        }
        if (count >= 3 && unsigned(header[0]) == 0xff && unsigned(header[1]) == 0xd8
                && unsigned(header[2]) == 0xff) {
            return new DetectedFile(AssetType.COVER, "image/jpeg", ".jpg");
        }
        if (count >= 8 && unsigned(header[0]) == 0x89 && header[1] == 'P' && header[2] == 'N'
                && header[3] == 'G' && unsigned(header[4]) == 0x0d && unsigned(header[5]) == 0x0a
                && unsigned(header[6]) == 0x1a && unsigned(header[7]) == 0x0a) {
            return new DetectedFile(AssetType.COVER, "image/png", ".png");
        }
        if (count >= 12 && ascii(header, 0, "RIFF") && ascii(header, 8, "WEBP")) {
            return new DetectedFile(AssetType.COVER, "image/webp", ".webp");
        }
        if (count >= 12 && ascii(header, 4, "ftyp")) {
            return new DetectedFile(AssetType.VIDEO, "video/mp4", ".mp4");
        }
        if (count >= 4 && unsigned(header[0]) == 0x1a && unsigned(header[1]) == 0x45
                && unsigned(header[2]) == 0xdf && unsigned(header[3]) == 0xa3) {
            return new DetectedFile(AssetType.VIDEO, "video/webm", ".webm");
        }
        throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "仅支持 JPEG、PNG、WebP、MP4 或 WebM");
    }

    private void verifyDeclaredContentType(String declared, String detected) {
        if (declared == null || declared.isBlank()
                || declared.equalsIgnoreCase("application/octet-stream")) return;
        String normalized = declared.toLowerCase(Locale.ROOT);
        boolean jpegAlias = detected.equals("image/jpeg") && normalized.equals("image/jpg");
        if (!normalized.equals(detected) && !jpegAlias) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "声明的 MIME 类型与文件内容不一致");
        }
    }

    private int probeDurationSeconds(Path video) {
        Process process;
        try {
            process = new ProcessBuilder(ffprobeExecutable, "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1", video.toString())
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "服务器未配置可用的 ffprobe", exception);
        }
        try {
            if (!process.waitFor(FFPROBE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "视频时长读取超时");
            }
            String output = new String(process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "无法读取视频时长");
            }
            String firstLine = output.lines().findFirst().orElse("");
            double duration = Double.parseDouble(firstLine);
            if (!Double.isFinite(duration) || duration <= 0 || duration > Integer.MAX_VALUE) {
                throw new NumberFormatException("duration out of range");
            }
            return (int) Math.ceil(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "视频时长读取被中断", exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "无法读取 ffprobe 输出", exception);
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "视频时长无效", exception);
        }
    }

    private Path safeStoragePath(String storageFilename) {
        if (storageFilename == null || !STORAGE_FILENAME.matcher(storageFilename).matches()) {
            throw new IllegalArgumentException("非法素材存储文件名");
        }
        Path path = root.resolve(storageFilename).normalize();
        if (!path.getParent().equals(root)) throw new IllegalArgumentException("非法素材路径");
        return path;
    }

    private void ensureRoot() {
        try {
            Files.createDirectories(root);
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("素材目录不可用");
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("无法创建素材目录", exception);
        }
    }

    private void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination);
        }
    }

    private static int unsigned(byte value) { return value & 0xff; }

    private static boolean ascii(byte[] bytes, int offset, String expected) {
        byte[] value = expected.getBytes(StandardCharsets.US_ASCII);
        if (offset + value.length > bytes.length) return false;
        for (int index = 0; index < value.length; index++) {
            if (bytes[offset + index] != value[index]) return false;
        }
        return true;
    }

    private static void deleteTemporary(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.error("临时上传文件清理失败，请按精确路径人工核查: {}", path, exception);
        }
    }

    record StoredUpload(AssetStorageBackend backend, String storageFilename, String objectKey,
                        String contentType, long sizeBytes, Integer durationSeconds) {}

    record QuarantinedAsset(AssetStorageBackend backend, String storageFilename, String objectKey,
                            Path pendingPath, OssAssetStorage.Quarantine ossQuarantine) {}

    private record DetectedFile(AssetType type, String contentType, String extension) {}
}
