package com.example.drama;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.models.CopyObjectRequest;
import com.aliyun.sdk.service.oss2.models.DeleteObjectRequest;
import com.aliyun.sdk.service.oss2.models.GetObjectRequest;
import com.aliyun.sdk.service.oss2.models.GetObjectResult;
import com.aliyun.sdk.service.oss2.models.HeadObjectResult;
import com.aliyun.sdk.service.oss2.models.ListObjectsRequest;
import com.aliyun.sdk.service.oss2.models.ListObjectsV2Request;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class OssAssetStorageTest {
    @TempDir Path temporary;

    @Test
    void uploadsPrivateObjectAndStreamsOnlyRequestedRange() throws Exception {
        OSSClient client = mock(OSSClient.class);
        OssAssetStorage storage = storage(client);
        Path file = temporary.resolve("cover.png");
        Files.write(file, new byte[]{1, 2, 3, 4, 5});
        String key = storage.objectKey("123e4567-e89b-12d3-a456-426614174000.png");

        storage.putFile(key, file, "image/png", 5);
        ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(put.capture());
        assertEquals("private", put.getValue().objectAcl());
        assertTrue(put.getValue().forbidOverwrite());
        assertEquals("test-bucket", put.getValue().bucket());
        assertEquals(key, put.getValue().key());

        HeadObjectResult head = HeadObjectResult.newBuilder()
                .headers(java.util.Map.of("Content-Length", "5")).build();
        when(client.headObject(any())).thenReturn(head);
        assertEquals(5, storage.size(key));

        CloseTrackingInputStream responseBody = new CloseTrackingInputStream(
                new byte[]{2, 3, 4, 99, 100});
        GetObjectResult result = GetObjectResult.newBuilder()
                .headers(java.util.Map.of("Content-Length", "3"))
                .innerBody(responseBody).build();
        when(client.getObject(any())).thenReturn(result);
        try (var input = storage.openRange(key, 1, 3)) {
            assertArrayEquals(new byte[]{2, 3, 4}, input.readAllBytes());
        }
        ArgumentCaptor<GetObjectRequest> get = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(client).getObject(get.capture());
        assertEquals("bytes=1-3", get.getValue().range());
        assertTrue(responseBody.closed);
    }

    @Test
    void quarantineTouchesOnlyExactAssetAndPrivateTemporaryKey() {
        OSSClient client = mock(OSSClient.class);
        OssAssetStorage storage = storage(client);
        String key = storage.objectKey("123e4567-e89b-12d3-a456-426614174000.mp4");

        OssAssetStorage.Quarantine quarantined = storage.quarantine(key);
        ArgumentCaptor<CopyObjectRequest> copy = ArgumentCaptor.forClass(CopyObjectRequest.class);
        ArgumentCaptor<DeleteObjectRequest> delete = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).copyObject(copy.capture());
        verify(client).deleteObject(delete.capture());
        assertEquals(key, copy.getValue().sourceKey());
        assertEquals("private", copy.getValue().objectAcl());
        assertEquals(quarantined.pendingKey(), copy.getValue().key());
        assertEquals(key, delete.getValue().key());
        verify(client, never()).listObjects(any(ListObjectsRequest.class));
        verify(client, never()).listObjectsV2(any(ListObjectsV2Request.class));

        storage.restore(quarantined);
        assertEquals(key, quarantined.objectKey());
    }

    @Test
    void rejectsKeysOutsideDedicatedNamespace() {
        OssAssetStorage storage = storage(mock(OSSClient.class));
        assertThrows(IllegalArgumentException.class,
                () -> storage.deleteUploadedObject("storysprout/books/cover.png"));
    }

    private OssAssetStorage storage(OSSClient client) {
        @SuppressWarnings("unchecked")
        ObjectProvider<OSSClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        OssProperties properties = new OssProperties();
        properties.setBucketName("test-bucket");
        return new OssAssetStorage(provider, properties);
    }

    private static final class CloseTrackingInputStream extends ByteArrayInputStream {
        private boolean closed;
        private CloseTrackingInputStream(byte[] bytes) { super(bytes); }
        @Override public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
