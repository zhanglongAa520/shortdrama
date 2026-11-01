package com.example.drama;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EpisodeUnlockRepository extends JpaRepository<EpisodeUnlock, Long> {
    boolean existsByUserIdAndEpisodeId(Long userId, Long episodeId);
    Optional<EpisodeUnlock> findByUserIdAndEpisodeId(Long userId, Long episodeId);
    List<EpisodeUnlock> findByUserIdOrderByGrantedAtDesc(Long userId);
    void deleteByUserIdAndEpisodeId(Long userId, Long episodeId);
    void deleteByEpisodeIdIn(Collection<Long> episodeIds);
    void deleteByEpisodeDramaId(Long dramaId);
}
