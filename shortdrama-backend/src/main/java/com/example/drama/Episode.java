package com.example.drama;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class Episode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    @Column(length = 2000)
    private String videoUrl;
    private int durationSeconds;
    private boolean free;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drama_id", nullable = false)
    private Drama drama;

    protected Episode() {}

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }
    public boolean isFree() { return free; }
    public void setFree(boolean free) { this.free = free; }
    public Drama getDrama() { return drama; }
    public void setDrama(Drama drama) { this.drama = drama; }
}
