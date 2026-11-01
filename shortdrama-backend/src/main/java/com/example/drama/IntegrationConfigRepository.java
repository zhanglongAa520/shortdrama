package com.example.drama;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IntegrationConfigRepository extends JpaRepository<IntegrationConfig, IntegrationProvider> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select config from IntegrationConfig config where config.provider = :provider")
    Optional<IntegrationConfig> findForUpdate(@Param("provider") IntegrationProvider provider);
}
