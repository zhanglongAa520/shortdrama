package com.example.drama;

import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaybackEventRepository extends JpaRepository<PlaybackEvent, String> {
    void deleteByDramaId(Long dramaId);
    void deleteByEpisodeIdIn(Collection<Long> episodeIds);
}
