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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "episode_unlock", uniqueConstraints = @UniqueConstraint(
        name = "uk_episode_unlock_user_episode", columnNames = {"user_id", "episode_id"}))
public class EpisodeUnlock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "episode_id", nullable = false)
    private Episode episode;
    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;
    protected EpisodeUnlock() {}
    @PrePersist void onCreate() { if (grantedAt == null) grantedAt = Instant.now(); }
    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public Episode getEpisode() { return episode; }
    public void setEpisode(Episode episode) { this.episode = episode; }
    public Instant getGrantedAt() { return grantedAt; }
}
