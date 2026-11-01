package com.example.drama;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DemoData {
    @Bean
    CommandLineRunner seedDemoDramas(DramaRepository dramas, EpisodeRepository episodeRepository,
                                     SeedMarkerRepository markers) {
        return args -> {
            if (!markers.existsById("initial-demo-data")) {
                dramas.save(create("时光来信", "一封来自未来的信，改写了她的命运。", "都市奇幻",
                    "/posters/after-rain.svg", "/api/media/demo/drama-1.mp4", DramaStatus.PUBLISHED,
                    "错投的信", "未来的线索"));
                dramas.save(create("深巷有光", "失意记者追查旧城秘密，在真相中重新找到方向。", "悬疑",
                    "/posters/palace.svg", "/api/media/demo/drama-2.mp4", DramaStatus.PUBLISHED,
                    "雨夜来客", "消失的证人"));
                dramas.save(create("心动倒计时", "两个互不相识的人，因一场误会走进彼此的生活。", "爱情",
                    "/posters/city.svg", "/api/media/demo/drama-3.mp4", DramaStatus.PUBLISHED,
                    "意外相遇", "没有说出口的话"));
                dramas.save(create("星河之下", "年轻的天文研究员发现，一段讯号暗藏失踪谜案。", "科幻",
                    "/posters/coast.svg", "/api/media/demo/drama-4.mp4", DramaStatus.DRAFT,
                    "来自远方", "夜空密码"));
                markers.save(new SeedMarker("initial-demo-data"));
            }
            var migratedEpisodes = dramas.findAll().stream().flatMap(drama -> drama.getEpisodes().stream()).filter(episode -> {
                if (episode.getVideoUrl().startsWith("/demo/")) {
                    episode.setVideoUrl("/api/media" + episode.getVideoUrl());
                    return true;
                }
                return false;
            }).toList();
            episodeRepository.saveAll(migratedEpisodes);
        };
    }

    private Drama create(String title, String description, String genre, String coverUrl,
                         String videoUrl, DramaStatus status, String first, String second) {
        Drama drama = new Drama();
        drama.setTitle(title);
        drama.setDescription(description);
        drama.setGenre(genre);
        drama.setCoverUrl(coverUrl);
        drama.setStatus(status);
        drama.addEpisode(episode(first, videoUrl, 12, true));
        drama.addEpisode(episode(second, videoUrl, 12, false));
        return drama;
    }

    private Episode episode(String title, String videoUrl, int durationSeconds, boolean free) {
        Episode episode = new Episode();
        episode.setTitle(title);
        episode.setVideoUrl(videoUrl);
        episode.setDurationSeconds(durationSeconds);
        episode.setFree(free);
        return episode;
    }
}
