package com.example.drama;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DramaViewRepository extends JpaRepository<DramaView, Long> {
    boolean existsByUserIdAndDramaId(Long userId, Long dramaId);
    void deleteByDramaId(Long dramaId);
}
