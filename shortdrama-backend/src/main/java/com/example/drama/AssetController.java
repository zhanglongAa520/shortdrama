package com.example.drama;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AssetController {
    private final AssetService assets;

    public AssetController(AssetService assets) {
        this.assets = assets;
    }

    @PostMapping(path = "/api/admin/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<MediaAssetDto.Response> upload(@RequestParam AssetType type,
                                                  @RequestPart("file") MultipartFile file,
                                                  Principal principal) {
        MediaAsset asset = assets.upload(type, file, principal.getName());
        return ResponseEntity.created(URI.create("/api/admin/assets/" + asset.getId()))
                .body(MediaAssetDto.Response.from(asset));
    }

    @GetMapping("/api/admin/assets")
    List<MediaAssetDto.Response> list(@RequestParam(required = false) AssetType type) {
        return assets.list(type).stream().map(MediaAssetDto.Response::from).toList();
    }

    @GetMapping("/api/admin/assets/{id}")
    MediaAssetDto.Response one(@PathVariable long id) {
        return MediaAssetDto.Response.from(assets.require(id));
    }

    @DeleteMapping("/api/admin/assets/{id}")
    ResponseEntity<Void> delete(@PathVariable long id) {
        assets.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/admin/assets/{id}/content")
    ResponseEntity<?> adminContent(@PathVariable long id,
                                   @org.springframework.web.bind.annotation.RequestHeader(
                                           value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        MediaAsset asset = assets.require(id);
        return content(asset, rangeHeader);
    }

    @GetMapping("/api/assets/{id}/content")
    ResponseEntity<?> coverContent(@PathVariable long id) {
        MediaAsset asset = assets.require(id);
        if (asset.getType() != AssetType.COVER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "视频素材必须通过分集播放接口访问");
        }
        return content(asset, null);
    }

    private ResponseEntity<?> content(MediaAsset asset, String rangeHeader) {
        StoredAssetResource stored = assets.requireContent(asset);
        MediaType mediaType = MediaType.parseMediaType(stored.contentType());
        String disposition = ContentDisposition.inline()
                .filename(asset.getOriginalFilename(), StandardCharsets.UTF_8)
                .build().toString();
        if (rangeHeader == null || rangeHeader.isBlank() || asset.getType() == AssetType.COVER) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.ACCEPT_RANGES, asset.getType() == AssetType.VIDEO ? "bytes" : "none")
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .contentType(mediaType)
                    .contentLength(stored.size())
                    .body(stored.resource(0, stored.size()));
        }

        List<HttpRange> ranges;
        try {
            ranges = HttpRange.parseRanges(rangeHeader);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        }
        if (ranges.size() != 1) {
            throw new ResponseStatusException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        }
        HttpRange range = ranges.getFirst();
        long start = range.getRangeStart(stored.size());
        long end = range.getRangeEnd(stored.size());
        if (start >= stored.size() || end < start) {
            throw new ResponseStatusException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        }
        long count = end - start + 1;
        Resource body = stored.resource(start, count);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE,
                        "bytes " + start + "-" + end + "/" + stored.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(mediaType)
                .contentLength(count)
                .body(body);
    }

}
