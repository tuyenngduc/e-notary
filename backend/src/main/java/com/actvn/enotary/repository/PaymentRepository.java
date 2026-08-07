package com.actvn.enotary.repository;

import com.actvn.enotary.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByRequest_RequestId(UUID requestId);
    
    @Query("SELECT FUNCTION('TO_CHAR', p.createdAt, 'YYYY-MM') as month, SUM(p.amount) " +
           "FROM Payment p " +
           "WHERE p.paymentStatus = com.actvn.enotary.enums.PaymentStatus.SUCCESS " +
           "GROUP BY FUNCTION('TO_CHAR', p.createdAt, 'YYYY-MM') " +
           "ORDER BY month")
    List<Object[]> getMonthlyRevenue();
}
