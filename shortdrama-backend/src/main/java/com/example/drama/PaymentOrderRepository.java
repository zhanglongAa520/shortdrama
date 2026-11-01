package com.example.drama;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, String> {
    List<PaymentOrder> findByUserIdOrderByCreatedAtDesc(Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentOrder p where p.orderNo = :orderNo")
    Optional<PaymentOrder> findForUpdate(@Param("orderNo") String orderNo);
}
