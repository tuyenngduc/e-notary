package com.actvn.enotary.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "blockchain_transactions")
@Data
public class BlockchainTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private NotaryRequest request;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(nullable = false, length = 64)
    private String documentHash;

    @Column(nullable = false, unique = true, length = 100)
    private String transactionHash;

    @Column(nullable = false)
    private Long blockNumber;

    @Column(nullable = false, length = 100)
    private String networkName = "Hyperledger Besu Local";

    @Column(nullable = false)
    private Long chainId = 1337L;

    @Column(nullable = false, length = 30)
    private String status = "CONFIRMED";

    @Column(nullable = false, length = 100)
    private String nodeName = "besu-validator-1";

    private OffsetDateTime createdAt = OffsetDateTime.now();
    private OffsetDateTime confirmedAt = OffsetDateTime.now();
}
