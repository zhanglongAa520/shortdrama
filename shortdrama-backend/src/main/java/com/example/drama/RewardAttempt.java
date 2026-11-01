package com.example.drama;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "reward_attempt", indexes = {
        @Index(name = "idx_reward_attempt_user_created", columnList = "user_id,created_at"),
        @Index(name = "idx_reward_attempt_status_expires", columnList = "status,expires_at"),
        @Index(name = "idx_reward_attempt_episode", columnList = "episode_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_reward_attempt_nonce", columnNames = "nonce"),
        @UniqueConstraint(name = "uk_reward_attempt_provider_transaction", columnNames = "provider_transaction_id")
})
public class RewardAttempt {
    @Id
    @Column(length = 36)
    private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "episode_id", nullable = false)
    private Episode episode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RewardPlatform platform;
    @Column(nullable = false, length = 64)
    private String nonce;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RewardAttemptStatus status;
    @Column(name = "expected_reward_name", nullable = false, length = 64)
    private String expectedRewardName;
    @Column(name = "expected_reward_amount", nullable = false)
    private int expectedRewardAmount;
    @Column(name = "provider_transaction_id", length = 128)
    private String providerTransactionId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "granted_at")
    private Instant grantedAt;

    protected RewardAttempt() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public Episode getEpisode() { return episode; }
    public void setEpisode(Episode episode) { this.episode = episode; }
    public RewardPlatform getPlatform() { return platform; }
    public void setPlatform(RewardPlatform platform) { this.platform = platform; }
    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }
    public RewardAttemptStatus getStatus() { return status; }
    public void setStatus(RewardAttemptStatus status) { this.status = status; }
    public String getExpectedRewardName() { return expectedRewardName; }
    public void setExpectedRewardName(String expectedRewardName) { this.expectedRewardName = expectedRewardName; }
    public int getExpectedRewardAmount() { return expectedRewardAmount; }
    public void setExpectedRewardAmount(int expectedRewardAmount) { this.expectedRewardAmount = expectedRewardAmount; }
    public String getProviderTransactionId() { return providerTransactionId; }
    public void setProviderTransactionId(String providerTransactionId) { this.providerTransactionId = providerTransactionId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getGrantedAt() { return grantedAt; }
    public void setGrantedAt(Instant grantedAt) { this.grantedAt = grantedAt; }
}
