package com.actvn.enotary.repository;

import com.actvn.enotary.entity.BlockchainTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BlockchainTransactionRepository extends JpaRepository<BlockchainTransaction, UUID> {
    Optional<BlockchainTransaction> findByDocument_DocumentId(UUID documentId);

    Optional<BlockchainTransaction> findTopByDocumentHashIgnoreCaseOrderByCreatedAtDesc(String documentHash);

    long countByStatus(String status);

    List<BlockchainTransaction> findTop50ByOrderByCreatedAtDesc();

    @Query("select coalesce(max(t.blockNumber), 0) from BlockchainTransaction t")
    long findLatestBlockNumber();

    @Query("select count(t) from BlockchainTransaction t where t.request.requestId = :requestId")
    long countByRequestId(@Param("requestId") UUID requestId);
}
