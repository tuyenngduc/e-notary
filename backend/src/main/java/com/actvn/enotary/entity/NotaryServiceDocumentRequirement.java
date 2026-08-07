package com.actvn.enotary.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "notary_service_document_requirements",
        uniqueConstraints = @UniqueConstraint(columnNames = {"service_type_id", "doc_type"})
)
@Data
public class NotaryServiceDocumentRequirement {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_type_id", nullable = false)
    private NotaryServiceType serviceType;

    @Column(name = "doc_type", nullable = false)
    private String docType;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
