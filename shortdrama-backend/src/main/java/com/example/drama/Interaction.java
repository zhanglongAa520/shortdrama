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
import java.time.Instant;

@Entity
public class Interaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InteractionType type;
    @Column(nullable = false, length = 500)
    private String text;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drama_id", nullable = false)
    private Drama drama;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id")
    private Episode episode;
    @Column(name = "at_seconds")
    private Double at;
    private Integer lane;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InteractionStatus status;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Interaction() {}

    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
    public Long getId() { return id; }
    public InteractionType getType() { return type; }
    public void setType(InteractionType type) { this.type = type; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Drama getDrama() { return drama; }
    public void setDrama(Drama drama) { this.drama = drama; }
    public Episode getEpisode() { return episode; }
    public void setEpisode(Episode episode) { this.episode = episode; }
    public Double getAt() { return at; }
    public void setAt(Double at) { this.at = at; }
    public Integer getLane() { return lane; }
    public void setLane(Integer lane) { this.lane = lane; }
    public InteractionStatus getStatus() { return status; }
    public void setStatus(InteractionStatus status) { this.status = status; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public Instant getCreatedAt() { return createdAt; }
}
