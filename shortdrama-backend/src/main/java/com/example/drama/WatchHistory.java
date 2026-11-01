package com.example.drama;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "watch_history", uniqueConstraints = @UniqueConstraint(
        name = "uk_watch_history_user_episode", columnNames = {"user_id", "episode_id"}))
public class WatchHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "drama_id", nullable = false)
    private Drama drama;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "episode_id", nullable = false)
    private Episode episode;
    @Column(name = "progress_seconds", nullable = false)
    private double progressSeconds;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    protected WatchHistory() {}
    @PrePersist @PreUpdate void touch() { updatedAt = Instant.now(); }
    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public Drama getDrama() { return drama; }
    public void setDrama(Drama drama) { this.drama = drama; }
    public Episode getEpisode() { return episode; }
    public void setEpisode(Episode episode) { this.episode = episode; }
    public double getProgressSeconds() { return progressSeconds; }
    public void setProgressSeconds(double progressSeconds) { this.progressSeconds = progressSeconds; }
    public Instant getUpdatedAt() { return updatedAt; }
}
