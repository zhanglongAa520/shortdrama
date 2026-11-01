package com.example.drama;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RewardAttemptRepository extends JpaRepository<RewardAttempt, String> {
    Optional<RewardAttempt> findByIdAndUserId(String id, Long userId);
    Optional<RewardAttempt> findByProviderTransactionId(String providerTransactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from RewardAttempt attempt where attempt.nonce = :nonce")
    Optional<RewardAttempt> findByNonceForUpdate(@Param("nonce") String nonce);
}
