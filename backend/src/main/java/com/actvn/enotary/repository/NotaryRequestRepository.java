package com.actvn.enotary.repository;

import com.actvn.enotary.entity.NotaryRequest;
import com.actvn.enotary.enums.RequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotaryRequestRepository extends JpaRepository<NotaryRequest, UUID> {
    List<NotaryRequest> findByClientUserId(UUID userId);

    Page<NotaryRequest> findByStatus(RequestStatus status, Pageable pageable);

    Page<NotaryRequest> findByStatusAndNotaryIsNull(RequestStatus status, Pageable pageable);

    Page<NotaryRequest> findByStatusOrNotaryUserId(RequestStatus status, UUID notaryUserId, Pageable pageable);

    Page<NotaryRequest> findByNotaryUserIdAndStatus(UUID notaryUserId, RequestStatus status, Pageable pageable);

    Page<NotaryRequest> findByNotaryUserId(UUID notaryUserId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from NotaryRequest r where r.requestId = :requestId")
    Optional<NotaryRequest> findByIdForUpdate(@Param("requestId") UUID requestId);

    long countByStatusIn(List<RequestStatus> statuses);

    @Query("SELECT r.serviceType, COUNT(r) FROM NotaryRequest r GROUP BY r.serviceType")
    List<Object[]> countRequestsByServiceType();

    @Modifying
    @Query("update NotaryRequest r set r.selectedTemplate = null where r.selectedTemplate.id = :templateId")
    int clearSelectedTemplate(@Param("templateId") UUID templateId);
}
