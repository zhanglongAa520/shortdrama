package com.example.drama;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class AssetStorageServiceOssTest {
    @TempDir Path temporary;

    @Test
    void validatesLocallyThenUploadsToDedicatedOssKey() throws Exception {
        MediaAssetRepository assets = mock(MediaAssetRepository.class);
        OssAssetStorage oss = mock(OssAssetStorage.class);
        when(oss.enabled()).thenReturn(true);
        when(oss.objectKey(any())).thenAnswer(invocation -> OssAssetStorage.PREFIX + invocation.getArgument(0));
        ArgumentCaptor<Path> uploadedPath = ArgumentCaptor.forClass(Path.class);
        doAnswer(invocation -> {
            Path path = invocation.getArgument(1);
            assertTrue(Files.isRegularFile(path));
            assertArrayEquals(png(), Files.readAllBytes(path));
            return null;
        }).when(oss).putFile(any(), uploadedPath.capture(), eq("image/png"), eq((long) png().length));
        AssetStorageService storage = new AssetStorageService(
                assets, oss, temporary.toString(), "ffprobe", 1024, 2048);

        AssetStorageService.StoredUpload stored = storage.store(
                new MockMultipartFile("file", "cover.png", "image/png", png()), AssetType.COVER);

        assertEquals(AssetStorageBackend.OSS, stored.backend());
        assertTrue(stored.objectKey().startsWith(OssAssetStorage.PREFIX));
        assertNull(stored.durationSeconds());
        verify(oss).putFile(eq(stored.objectKey()), any(), eq("image/png"), eq((long) png().length));
        try (var files = Files.list(temporary)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void opensHistoricalLocalAssetWithoutOss() throws Exception {
        MediaAssetRepository assets = mock(MediaAssetRepository.class);
        OssAssetStorage oss = mock(OssAssetStorage.class);
        when(oss.enabled()).thenReturn(false);
        AssetStorageService storage = new AssetStorageService(
                assets, oss, temporary.toString(), "ffprobe", 1024, 2048);
        AssetStorageService.StoredUpload stored = storage.store(
                new MockMultipartFile("file", "cover.png", "image/png", png()), AssetType.COVER);
        MediaAsset asset = new MediaAsset();
        asset.setStorageFilename(stored.storageFilename());
        asset.setStorageBackend(AssetStorageBackend.LOCAL);
        asset.setContentType(stored.contentType());
        asset.setSizeBytes(stored.sizeBytes());

        StoredAssetResource resource = storage.open(asset).orElseThrow();
        try (var input = resource.resource(1, 4).getInputStream()) {
            assertArrayEquals(new byte[]{'P', 'N', 'G', 13}, input.readAllBytes());
        }
        verify(oss, never()).openRange(any(), anyLong(), anyLong());
    }

    private static byte[] png() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0, 0, 0, 0, 0};
    }
}
