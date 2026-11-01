package com.example.drama;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

class AssetServiceCompensationTest {
    @Test
    void databaseUploadFailureDeletesOnlyNewOssObject() {
        MediaAssetRepository repository = mock(MediaAssetRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        AssetStorageService storage = mock(AssetStorageService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AppUser uploader = mock(AppUser.class);
        String key = "ai-short-drama/assets/123e4567-e89b-12d3-a456-426614174000.png";
        AssetStorageService.StoredUpload stored = new AssetStorageService.StoredUpload(
                AssetStorageBackend.OSS, "123e4567-e89b-12d3-a456-426614174000.png", key,
                "image/png", 16, null);
        when(users.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(uploader));
        when(storage.store(any(), eq(AssetType.COVER))).thenReturn(stored);
        when(repository.saveAndFlush(any())).thenThrow(new IllegalStateException("database unavailable"));
        AssetService service = new AssetService(repository, users, storage, jdbc);

        assertThrows(IllegalStateException.class, () -> service.upload(AssetType.COVER,
                new MockMultipartFile("file", "cover.png", "image/png", new byte[]{1}), "admin"));

        verify(storage).deleteFailedUpload(stored);
    }

    @Test
    void databaseDeleteFailureRestoresQuarantinedOssObject() {
        MediaAssetRepository repository = mock(MediaAssetRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        AssetStorageService storage = mock(AssetStorageService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MediaAsset asset = mock(MediaAsset.class);
        String key = "ai-short-drama/assets/123e4567-e89b-12d3-a456-426614174000.mp4";
        String pending = "ai-short-drama/assets/.delete/223e4567-e89b-12d3-a456-426614174000-"
                + "123e4567-e89b-12d3-a456-426614174000.mp4";
        AssetStorageService.QuarantinedAsset quarantined = new AssetStorageService.QuarantinedAsset(
                AssetStorageBackend.OSS, "123e4567-e89b-12d3-a456-426614174000.mp4", key,
                null, new OssAssetStorage.Quarantine(key, pending));
        when(repository.findById(7L)).thenReturn(Optional.of(asset));
        when(asset.getType()).thenReturn(AssetType.VIDEO);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any())).thenReturn(0L);
        when(storage.quarantine(asset)).thenReturn(quarantined);
        doThrow(new IllegalStateException("database unavailable")).when(repository).delete(asset);
        AssetService service = new AssetService(repository, users, storage, jdbc);

        assertThrows(IllegalStateException.class, () -> service.delete(7L));

        verify(storage).restore(quarantined);
    }
}
