package com.example.drama;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/media")
public class MediaController {
    private static final Set<String> ALLOWED = Set.of(
            "drama-1.mp4", "drama-2.mp4", "drama-3.mp4", "drama-4.mp4");
    private final Path mediaRoot;
    private final EpisodeEntitlementService entitlements;
    private final AssetStorageService assetStorage;

    public MediaController(@Value("${app.media-root:./media/demo}") String mediaRoot,
                           EpisodeEntitlementService entitlements, AssetStorageService assetStorage) {
        this.mediaRoot = Path.of(mediaRoot).toAbsolutePath().normalize();
        this.entitlements = entitlements;
        this.assetStorage = assetStorage;
    }

    @GetMapping("/demo/{filename:.+}")
    public ResponseEntity<?> video(@PathVariable String filename,
                                   @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader)
            throws IOException {
        if (!ALLOWED.contains(filename)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Path file = mediaRoot.resolve(filename).normalize();
        if (!file.getParent().equals(mediaRoot) || !Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return resource(file, "video/mp4", rangeHeader);
    }

    @GetMapping("/episodes/{episodeId}")
    public ResponseEntity<?> episodeVideo(@PathVariable Long episodeId,
                                          @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
                                          org.springframework.security.core.Authentication authentication)
            throws IOException {
        Episode episode = entitlements.requireWatchable(authentication.getName(), episodeId);
        String reference = episode.getVideoUrl();
        if (reference.startsWith("asset:")) {
            StoredAssetResource stored = assetStorage.openVideoReference(reference)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "视频素材不存在"));
            return resource(stored, rangeHeader);
        }
        String prefix = "/api/media/demo/";
        if (!reference.startsWith(prefix)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该分集不是平台托管视频");
        }
        String filename = reference.substring(prefix.length());
        if (!ALLOWED.contains(filename)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "视频不存在");
        Path file = mediaRoot.resolve(filename).normalize();
        if (!file.getParent().equals(mediaRoot) || !Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "视频不存在");
        }
        return resource(file, "video/mp4", rangeHeader);
    }

    private ResponseEntity<?> resource(Path file, String contentType, String rangeHeader) throws IOException {
        return resource(StoredAssetResource.local(file, contentType, Files.size(file)), rangeHeader);
    }

    private ResponseEntity<?> resource(StoredAssetResource stored, String rangeHeader) {
        long length = stored.size();
        MediaType mediaType = MediaType.parseMediaType(stored.contentType());
        if (rangeHeader == null || rangeHeader.isBlank()) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentType(mediaType)
                    .contentLength(length)
                    .body(stored.resource(0, length));
        }
        List<HttpRange> ranges;
        try {
            ranges = HttpRange.parseRanges(rangeHeader);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        }
        if (ranges.size() != 1) throw new ResponseStatusException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        HttpRange range = ranges.getFirst();
        long start = range.getRangeStart(length);
        long end = range.getRangeEnd(length);
        if (start >= length || end < start) {
            throw new ResponseStatusException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        }
        long count = end - start + 1;
        Resource ranged = stored.resource(start, count);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + length)
                .contentType(mediaType)
                .contentLength(count)
                .body(ranged);
    }

}
