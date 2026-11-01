package com.example.drama;

import com.example.drama.DramaDto.DramaRequest;
import com.example.drama.DramaDto.DramaResponse;
import com.example.drama.DramaDto.EpisodeRequest;
import com.example.drama.DramaDto.EpisodeResponse;
import com.example.drama.DramaDto.Overview;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DramaService {
    private final DramaRepository dramas;
    private final EpisodeRepository episodes;
    private final InteractionRepository interactions;
    private final EpisodeEntitlementService entitlements;
    private final FavoriteRepository favorites;
    private final WatchHistoryRepository histories;
    private final EpisodeUnlockRepository unlocks;
    private final DramaViewRepository views;
    private final PlaybackEventRepository playbackEvents;
    private final SocialCountCache socialCache;

    public DramaService(DramaRepository dramas, EpisodeRepository episodes, InteractionRepository interactions,
                        EpisodeEntitlementService entitlements, FavoriteRepository favorites,
                        WatchHistoryRepository histories, EpisodeUnlockRepository unlocks,
                        DramaViewRepository views, PlaybackEventRepository playbackEvents,
                        SocialCountCache socialCache) {
        this.dramas = dramas;
        this.episodes = episodes;
        this.interactions = interactions;
        this.entitlements = entitlements;
        this.favorites = favorites;
        this.histories = histories;
        this.unlocks = unlocks;
        this.views = views;
        this.playbackEvents = playbackEvents;
        this.socialCache = socialCache;
    }

    @Transactional(readOnly = true)
    public List<DramaResponse> published(String username) {
        return dramas.findByStatusOrderByIdAsc(DramaStatus.PUBLISHED).stream()
                .map(drama -> toPublicResponse(drama, username)).toList();
    }

    @Transactional(readOnly = true)
    public DramaResponse published(Long id, String username) {
        return toPublicResponse(dramas.findByIdAndStatus(id, DramaStatus.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "短剧不存在")), username);
    }

    @Transactional(readOnly = true)
    public DramaDto.RecommendationsResponse recommendations(String username) {
        List<DramaResponse> featured = dramas
                .findByStatusAndFeaturedTrueOrderByRecommendationRankAscViewCountDescIdAsc(DramaStatus.PUBLISHED)
                .stream().map(drama -> toPublicResponse(drama, username)).toList();
        List<DramaResponse> hot = dramas
                .findByStatusOrderByViewCountDescRecommendationRankAscIdAsc(DramaStatus.PUBLISHED)
                .stream().map(drama -> toPublicResponse(drama, username)).toList();
        return new DramaDto.RecommendationsResponse(featured, hot);
    }

    @Transactional(readOnly = true)
    public List<DramaResponse> all() {
        return dramas.findAllByOrderByIdAsc().stream().map(this::toAdminResponse).toList();
    }

    @Transactional
    public DramaResponse create(DramaRequest request) {
        Drama drama = new Drama();
        apply(drama, request, false);
        return toAdminResponse(dramas.saveAndFlush(drama));
    }

    @Transactional
    public DramaResponse update(Long id, DramaRequest request) {
        Drama drama = dramas.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "短剧不存在"));
        apply(drama, request, true);
        return toAdminResponse(dramas.saveAndFlush(drama));
    }

    @Transactional
    public void delete(Long id) {
        Drama drama = dramas.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "短剧不存在"));
        interactions.deleteByDramaId(id);
        histories.deleteByDramaId(id);
        favorites.deleteByDramaId(id);
        unlocks.deleteByEpisodeDramaId(id);
        views.deleteByDramaId(id);
        playbackEvents.deleteByDramaId(id);
        dramas.delete(drama);
        socialCache.invalidateAfterCommit(id);
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        return new Overview(dramas.count(), dramas.countByStatus(DramaStatus.PUBLISHED), episodes.count());
    }

    @Transactional
    public DramaResponse recommend(Long id, boolean featured, int recommendationRank) {
        Drama drama = dramas.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "短剧不存在"));
        drama.setFeatured(featured);
        drama.setRecommendationRank(recommendationRank);
        return toAdminResponse(drama);
    }

    private void apply(Drama drama, DramaRequest request, boolean updating) {
        if (request.status() == DramaStatus.PUBLISHED && request.episodes().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已发布短剧至少需要一集");
        }
        drama.setTitle(request.title().trim());
        drama.setDescription(request.description() == null ? "" : request.description().trim());
        drama.setGenre(request.genre().trim());
        drama.setCoverUrl(request.coverUrl().trim());
        drama.setStatus(request.status());

        Map<Long, Episode> existing = new HashMap<>();
        for (Episode episode : drama.getEpisodes()) {
            existing.put(episode.getId(), episode);
        }
        Set<Long> seenIds = new HashSet<>();
        List<Episode> ordered = new ArrayList<>();
        for (EpisodeRequest input : request.episodes()) {
            Episode episode;
            if (input.id() == null) {
                episode = new Episode();
            } else {
                if (!updating || !seenIds.add(input.id()) || !existing.containsKey(input.id())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分集 ID 无效: " + input.id());
                }
                episode = existing.get(input.id());
            }
            episode.setTitle(input.title().trim());
            episode.setVideoUrl(input.videoUrl().trim());
            episode.setDurationSeconds(input.durationSeconds());
            episode.setFree(input.free());
            if (!episode.isFree() && !entitlements.isManagedVideoReference(episode.getVideoUrl())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "付费分集必须使用平台托管视频");
            }
            ordered.add(episode);
        }
        Set<Long> retainedIds = ordered.stream().map(Episode::getId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        List<Long> removedIds = drama.getEpisodes().stream().map(Episode::getId)
                .filter(java.util.Objects::nonNull).filter(id -> !retainedIds.contains(id)).toList();
        if (!removedIds.isEmpty()) interactions.deleteByEpisodeIdIn(removedIds);
        if (!removedIds.isEmpty()) socialCache.invalidateAfterCommit(drama.getId());
        if (!removedIds.isEmpty()) histories.deleteByEpisodeIdIn(removedIds);
        if (!removedIds.isEmpty()) unlocks.deleteByEpisodeIdIn(removedIds);
        if (!removedIds.isEmpty()) playbackEvents.deleteByEpisodeIdIn(removedIds);
        drama.getEpisodes().clear();
        for (Episode episode : ordered) {
            drama.addEpisode(episode);
        }
    }

    public DramaResponse toPublicResponse(Drama drama, String username) {
        List<EpisodeResponse> episodeResponses = drama.getEpisodes().stream()
                .map(e -> publicEpisodeResponse(e, username))
                .toList();
        return new DramaResponse(drama.getId(), drama.getTitle(), drama.getDescription(),
                drama.getGenre(), drama.getCoverUrl(), drama.getStatus(), drama.isFeatured(),
                drama.getRecommendationRank(), drama.getViewCount(), episodeResponses);
    }

    public EpisodeResponse publicEpisodeResponse(Episode episode, String username) {
        boolean locked = !entitlements.canWatch(username, episode);
        String playableUrl = null;
        if (!locked) {
            playableUrl = entitlements.isManagedVideoReference(episode.getVideoUrl())
                    ? "/api/media/episodes/" + episode.getId() : episode.getVideoUrl();
        }
        return new EpisodeResponse(episode.getId(), episode.getTitle(),
                playableUrl, episode.getDurationSeconds(),
                episode.isFree(), locked);
    }

    private DramaResponse toAdminResponse(Drama drama) {
        List<EpisodeResponse> episodeResponses = drama.getEpisodes().stream()
                .map(e -> new EpisodeResponse(e.getId(), e.getTitle(), e.getVideoUrl(),
                        e.getDurationSeconds(), e.isFree(), false)).toList();
        return new DramaResponse(drama.getId(), drama.getTitle(), drama.getDescription(),
                drama.getGenre(), drama.getCoverUrl(), drama.getStatus(), drama.isFeatured(),
                drama.getRecommendationRank(), drama.getViewCount(), episodeResponses);
    }
}
