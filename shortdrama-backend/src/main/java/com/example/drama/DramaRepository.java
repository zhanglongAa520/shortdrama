package com.example.drama;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DramaRepository extends JpaRepository<Drama, Long> {
    List<Drama> findByStatusOrderByIdAsc(DramaStatus status);
    List<Drama> findAllByOrderByIdAsc();
    List<Drama> findByStatusAndFeaturedTrueOrderByRecommendationRankAscViewCountDescIdAsc(DramaStatus status);
    List<Drama> findByStatusOrderByViewCountDescRecommendationRankAscIdAsc(DramaStatus status);
    Optional<Drama> findByIdAndStatus(Long id, DramaStatus status);
    long countByStatus(DramaStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Drama d where d.id = :id")
    Optional<Drama> findForUpdate(@Param("id") Long id);
}
