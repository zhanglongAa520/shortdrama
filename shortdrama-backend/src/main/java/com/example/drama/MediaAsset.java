package com.example.drama;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "media_asset", uniqueConstraints = {
        @UniqueConstraint(name = "uk_media_asset_storage_filename", columnNames = "storage_filename"),
        @UniqueConstraint(name = "uk_media_asset_object_key", columnNames = "object_key")
})
public class MediaAsset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AssetType type;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "storage_filename", nullable = false)
    private String storageFilename;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_backend", nullable = false, length = 16)
    private AssetStorageBackend storageBackend = AssetStorageBackend.LOCAL;

    @Column(name = "object_key", length = 512)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private AppUser createdBy;

    protected MediaAsset() {}

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public AssetType getType() { return type; }
    public void setType(AssetType type) { this.type = type; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getStorageFilename() { return storageFilename; }
    public void setStorageFilename(String storageFilename) { this.storageFilename = storageFilename; }
    public AssetStorageBackend getStorageBackend() { return storageBackend; }
    public void setStorageBackend(AssetStorageBackend storageBackend) { this.storageBackend = storageBackend; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
    public Instant getCreatedAt() { return createdAt; }
    public AppUser getCreatedBy() { return createdBy; }
    public void setCreatedBy(AppUser createdBy) { this.createdBy = createdBy; }
}
