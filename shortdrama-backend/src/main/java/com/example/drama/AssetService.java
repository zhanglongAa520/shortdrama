package com.example.drama;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AssetService {
    private final MediaAssetRepository assets;
    private final AppUserRepository users;
    private final AssetStorageService storage;
    private final JdbcTemplate jdbc;

    public AssetService(MediaAssetRepository assets, AppUserRepository users,
                        AssetStorageService storage, JdbcTemplate jdbc) {
        this.assets = assets;
        this.users = users;
        this.storage = storage;
        this.jdbc = jdbc;
    }

    MediaAsset upload(AssetType type, MultipartFile file, String username) {
        if (type == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "素材类型不能为空");
        AppUser uploader = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录"));
        AssetStorageService.StoredUpload stored = storage.store(file, type);
        try {
            MediaAsset asset = new MediaAsset();
            asset.setType(type);
            asset.setOriginalFilename(safeOriginalFilename(file.getOriginalFilename()));
            asset.setStorageFilename(stored.storageFilename());
            asset.setStorageBackend(stored.backend());
            asset.setObjectKey(stored.objectKey());
            asset.setContentType(stored.contentType());
            asset.setSizeBytes(stored.sizeBytes());
            asset.setDurationSeconds(stored.durationSeconds());
            asset.setCreatedBy(uploader);
            return assets.saveAndFlush(asset);
        } catch (RuntimeException exception) {
            try {
                storage.deleteFailedUpload(stored);
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    List<MediaAsset> list(AssetType type) {
        return type == null
                ? assets.findAllByOrderByCreatedAtDesc()
                : assets.findByTypeOrderByCreatedAtDesc(type);
    }

    MediaAsset require(long id) {
        return assets.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "素材不存在"));
    }

    StoredAssetResource requireContent(MediaAsset asset) {
        return storage.open(asset)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "素材文件不存在"));
    }

    void delete(long id) {
        MediaAsset asset = require(id);
        if (referenceCount(asset) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "素材正在被剧目或分集引用");
        }
        AssetStorageService.QuarantinedAsset quarantined =
                storage.quarantine(asset);
        try {
            assets.delete(asset);
        } catch (RuntimeException exception) {
            try {
                storage.restore(quarantined);
            } catch (RuntimeException restoreFailure) {
                exception.addSuppressed(restoreFailure);
            }
            throw exception;
        }
        storage.purge(quarantined);
    }

    private long referenceCount(MediaAsset asset) {
        String sql;
        String reference;
        if (asset.getType() == AssetType.COVER) {
            sql = "SELECT COUNT(*) FROM drama WHERE cover_url = ?";
            reference = "/api/assets/" + asset.getId() + "/content";
        } else {
            sql = "SELECT COUNT(*) FROM episode WHERE video_url = ?";
            reference = "asset:" + asset.getId();
        }
        Long count = jdbc.queryForObject(sql, Long.class, reference);
        return count == null ? 0 : count;
    }

    private String safeOriginalFilename(String supplied) {
        String value = supplied == null ? "" : supplied.replace('\\', '/');
        int separator = value.lastIndexOf('/');
        if (separator >= 0) value = value.substring(separator + 1);
        value = value.replaceAll("[\\p{Cntrl}]", "").strip();
        if (value.isBlank()) value = "upload";
        if (value.length() > 200) value = value.substring(value.length() - 200);
        return value;
    }
}
