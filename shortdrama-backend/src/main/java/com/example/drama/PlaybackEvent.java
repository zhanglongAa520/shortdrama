package com.example.drama;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "playback_event", indexes = {
        @Index(name = "idx_playback_event_started", columnList = "started_at"),
        @Index(name = "idx_playback_event_user_started", columnList = "user_id,started_at"),
        @Index(name = "idx_playback_event_drama_started", columnList = "drama_id,started_at"),
        @Index(name = "idx_playback_event_episode_started", columnList = "episode_id,started_at")
})
public class PlaybackEvent {
    @Id
    @Column(length = 36)
    private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drama_id", nullable = false)
    private Drama drama;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "episode_id", nullable = false)
    private Episode episode;
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    protected PlaybackEvent() {}

    @PrePersist void onCreate() { if (startedAt == null) startedAt = Instant.now(); }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public Drama getDrama() { return drama; }
    public void setDrama(Drama drama) { this.drama = drama; }
    public Episode getEpisode() { return episode; }
    public void setEpisode(Episode episode) { this.episode = episode; }
    public Instant getStartedAt() { return startedAt; }
    void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
}
