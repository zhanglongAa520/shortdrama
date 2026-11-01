package com.example.drama;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;

public record StoredAssetResource(String description, String contentType, long size, RangeOpener opener) {
    public StoredAssetResource {
        if (size <= 0) throw new IllegalArgumentException("素材大小必须大于 0");
    }

    static StoredAssetResource local(Path path, String contentType, long size) {
        return new StoredAssetResource(path.toString(), contentType, size, (start, count) -> {
            InputStream input = Files.newInputStream(path);
            try {
                input.skipNBytes(start);
                return new LimitedInputStream(input, count);
            } catch (IOException exception) {
                input.close();
                throw exception;
            }
        });
    }

    public Resource resource(long start, long count) {
        if (start < 0 || count <= 0 || start > size - count) {
            throw new IllegalArgumentException("非法素材 Range");
        }
        return new AbstractResource() {
            @Override public String getDescription() {
                return description + " range [" + start + "," + (start + count - 1) + "]";
            }
            @Override public long contentLength() { return count; }
            @Override public InputStream getInputStream() throws IOException { return opener.open(start, count); }
        };
    }

    @FunctionalInterface
    public interface RangeOpener {
        InputStream open(long start, long count) throws IOException;
    }

    private static final class LimitedInputStream extends FilterInputStream {
        private long remaining;
        private LimitedInputStream(InputStream input, long remaining) {
            super(input);
            this.remaining = remaining;
        }
        @Override public int read() throws IOException {
            if (remaining == 0) return -1;
            int value = super.read();
            if (value >= 0) remaining--;
            return value;
        }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            if (remaining == 0) return -1;
            int read = super.read(bytes, offset, (int) Math.min(length, remaining));
            if (read > 0) remaining -= read;
            return read;
        }
    }
}
