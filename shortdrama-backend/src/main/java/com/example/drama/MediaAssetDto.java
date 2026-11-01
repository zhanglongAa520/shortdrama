package com.example.drama;

import java.time.Instant;

public final class MediaAssetDto {
    private MediaAssetDto() {}

    public record Response(Long id, AssetType type, String originalFilename, String contentType,
                           long sizeBytes, Integer durationSeconds, String reference,
                           String previewUrl, Instant createdAt) {
        static Response from(MediaAsset asset) {
            String reference = asset.getType() == AssetType.COVER
                    ? "/api/assets/" + asset.getId() + "/content"
                    : "asset:" + asset.getId();
            return new Response(asset.getId(), asset.getType(), asset.getOriginalFilename(),
                    asset.getContentType(), asset.getSizeBytes(), asset.getDurationSeconds(),
                    reference, "/api/admin/assets/" + asset.getId() + "/content",
                    asset.getCreatedAt());
        }
    }
}
