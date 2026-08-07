package com.actvn.enotary.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "documents")
@Data
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID documentId;

    @ManyToOne
    @JoinColumn(name = "request_id", nullable = false)
    private NotaryRequest request;

    private String filePath;
    private String originalFileName;
    private String contentType;
    private Long fileSize;

    private String docType;

    private String fileHash;
    private OffsetDateTime createdAt = OffsetDateTime.now();
    private OffsetDateTime updatedAt;
}
