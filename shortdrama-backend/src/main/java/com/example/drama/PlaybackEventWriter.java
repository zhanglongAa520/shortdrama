package com.example.drama;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PlaybackEventWriter {
    private final EntityManager entityManager;

    public PlaybackEventWriter(EntityManager entityManager) { this.entityManager = entityManager; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PlaybackEvent create(String id, Long userId, Long dramaId, Long episodeId) {
        PlaybackEvent event = new PlaybackEvent();
        event.setId(id);
        event.setUser(entityManager.getReference(AppUser.class, userId));
        event.setDrama(entityManager.getReference(Drama.class, dramaId));
        event.setEpisode(entityManager.getReference(Episode.class, episodeId));
        entityManager.persist(event);
        entityManager.flush();
        return event;
    }
}
