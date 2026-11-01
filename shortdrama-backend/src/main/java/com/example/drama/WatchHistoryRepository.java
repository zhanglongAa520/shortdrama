package com.example.drama;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchHistoryRepository extends JpaRepository<WatchHistory, Long> {
    List<WatchHistory> findByUserIdOrderByUpdatedAtDesc(Long userId);
    Optional<WatchHistory> findByUserIdAndEpisodeId(Long userId, Long episodeId);
    void deleteByUserIdAndDramaId(Long userId, Long dramaId);
    void deleteByUserId(Long userId);
    void deleteByDramaId(Long dramaId);
    void deleteByEpisodeIdIn(Collection<Long> episodeIds);
}
