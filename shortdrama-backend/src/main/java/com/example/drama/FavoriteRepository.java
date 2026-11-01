package com.example.drama;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    List<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Favorite> findByUserIdAndDramaId(Long userId, Long dramaId);
    long deleteByUserIdAndDramaId(Long userId, Long dramaId);
    void deleteByDramaId(Long dramaId);
}
