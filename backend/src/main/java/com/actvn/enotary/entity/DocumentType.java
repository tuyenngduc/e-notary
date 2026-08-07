package com.actvn.enotary.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Entity
@Table(name = "document_types")
@Data
public class DocumentType {
    @Id
    @Column(length = 80)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String source = "USER_UPLOAD";

    @Column(nullable = false)
    private String allowedFileGroup = "DOCUMENT";

    @Column(nullable = false)
    private Boolean isActive = true;

    @Column(nullable = false)
    private Boolean isSystem = false;

    @Column(nullable = false)
    private Integer sortOrder = 0;

    @Column(updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
