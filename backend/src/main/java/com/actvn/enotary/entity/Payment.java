package com.actvn.enotary.entity;

import com.actvn.enotary.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Data
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID paymentId;

    @OneToOne
    @JoinColumn(name = "request_id", nullable = false)
    private NotaryRequest request;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    private String transactionReference;
    private String paymentMethod;
    private OffsetDateTime createdAt = OffsetDateTime.now();
    private OffsetDateTime paidAt;
}
