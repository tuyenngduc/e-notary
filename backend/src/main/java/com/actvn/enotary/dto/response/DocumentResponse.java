package com.actvn.enotary.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class DocumentResponse {
    private UUID documentId;
    private UUID requestId;
    private String filePath;
    private String originalFileName;
    private String displayName;
    private String contentType;
    private Long fileSize;
    private String absolutePath;
    private String docType;
    private String fileHash;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static DocumentResponse fromEntity(com.actvn.enotary.entity.Document d) {
        String displayName = resolveDisplayName(d);
        return DocumentResponse.builder()
                .documentId(d.getDocumentId())
                .requestId(d.getRequest() != null ? d.getRequest().getRequestId() : null)
                .filePath(d.getFilePath())
                .originalFileName(d.getOriginalFileName())
                .displayName(displayName)
                .contentType(d.getContentType())
                .fileSize(d.getFileSize())
                .absolutePath(null)
                .docType(d.getDocType())
                .fileHash(d.getFileHash())
                .updatedAt(d.getUpdatedAt())
                .createdAt(d.getCreatedAt())
                .build();
    }

    private static String resolveDisplayName(com.actvn.enotary.entity.Document d) {
        if (d.getOriginalFileName() != null && !d.getOriginalFileName().isBlank()) {
            return d.getOriginalFileName();
        }
        if (d.getFilePath() == null || d.getFilePath().isBlank()) {
            return null;
        }

        String fileName = d.getFilePath().replace("\\", "/");
        int slashIndex = fileName.lastIndexOf('/');
        if (slashIndex >= 0) {
            fileName = fileName.substring(slashIndex + 1);
        }
        return fileName.replaceFirst("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}-", "");
    }
}
