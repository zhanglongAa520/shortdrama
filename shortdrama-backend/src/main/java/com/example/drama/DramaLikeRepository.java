package com.example.drama;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DramaLikeRepository extends JpaRepository<DramaLike, Long> {
    boolean existsByUserIdAndDramaId(Long userId, Long dramaId);
    long deleteByUserIdAndDramaId(Long userId, Long dramaId);
}
