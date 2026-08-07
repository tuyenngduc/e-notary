package com.actvn.enotary.entity;

import com.actvn.enotary.enums.RequestStatus;
import com.actvn.enotary.enums.ServiceType;
import com.actvn.enotary.enums.ContractType;
import jakarta.persistence.*;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "notary_requests")
@Data
public class NotaryRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID requestId;

    @ManyToOne
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @ManyToOne
    @JoinColumn(name = "notary_id")
    private User notary;

    @Enumerated(EnumType.STRING)
    private ServiceType serviceType;

    @Enumerated(EnumType.STRING)
    private ContractType contractType;
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_template_id")
    private ContractTemplate selectedTemplate;

    @Enumerated(EnumType.STRING)
    private RequestStatus status = RequestStatus.NEW;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL)
    private List<Document> documents;

}
