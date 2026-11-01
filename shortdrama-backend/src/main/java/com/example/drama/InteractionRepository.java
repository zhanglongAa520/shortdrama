package com.example.drama;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface InteractionRepository extends JpaRepository<Interaction, Long>, JpaSpecificationExecutor<Interaction> {
    List<Interaction> findByDramaIdAndTypeAndStatusOrderByCreatedAtAsc(
            Long dramaId, InteractionType type, InteractionStatus status);
    List<Interaction> findByDramaIdAndTypeAndEpisodeIdAndStatusOrderByCreatedAtAsc(
            Long dramaId, InteractionType type, Long episodeId, InteractionStatus status);
    void deleteByDramaId(Long dramaId);
    void deleteByEpisodeIdIn(Collection<Long> episodeIds);
}
