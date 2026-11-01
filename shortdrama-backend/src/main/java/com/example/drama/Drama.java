package com.example.drama;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Drama {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    @jakarta.persistence.Column(length = 4000)
    private String description;
    private String genre;
    @jakarta.persistence.Column(length = 2000)
    private String coverUrl;
    @Enumerated(EnumType.STRING)
    private DramaStatus status;
    @jakarta.persistence.Column(nullable = false)
    private boolean featured;
    @jakarta.persistence.Column(name = "recommendation_rank", nullable = false)
    private int recommendationRank = 100;
    @jakarta.persistence.Column(name = "view_count", nullable = false)
    private long viewCount;
    @OneToMany(mappedBy = "drama", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderColumn(name = "episode_position")
    private List<Episode> episodes = new ArrayList<>();

    protected Drama() {}

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public DramaStatus getStatus() { return status; }
    public void setStatus(DramaStatus status) { this.status = status; }
    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) { this.featured = featured; }
    public int getRecommendationRank() { return recommendationRank; }
    public void setRecommendationRank(int recommendationRank) { this.recommendationRank = recommendationRank; }
    public long getViewCount() { return viewCount; }
    public void setViewCount(long viewCount) { this.viewCount = viewCount; }
    public List<Episode> getEpisodes() { return episodes; }
    public void addEpisode(Episode episode) {
        episodes.add(episode);
        episode.setDrama(this);
    }
}
