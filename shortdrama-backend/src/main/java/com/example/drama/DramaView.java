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
@Table(name = "drama_view", uniqueConstraints = @UniqueConstraint(
        name = "uk_drama_view_user_drama", columnNames = {"user_id", "drama_id"}))
public class DramaView {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "drama_id", nullable = false)
    private Drama drama;
    @Column(name = "first_viewed_at", nullable = false, updatable = false)
    private Instant firstViewedAt;
    protected DramaView() {}
    @PrePersist void onCreate() { if (firstViewedAt == null) firstViewedAt = Instant.now(); }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public Drama getDrama() { return drama; }
    public void setDrama(Drama drama) { this.drama = drama; }
}
