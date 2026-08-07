package com.actvn.enotary.dto.response;

import com.actvn.enotary.entity.DocumentType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentTypeResponse {
    private String code;
    private String name;
    private String description;
    private String source;
    private String allowedFileGroup;
    private Boolean isActive;
    private Boolean isSystem;
    private Integer sortOrder;

    public static DocumentTypeResponse fromEntity(DocumentType documentType) {
        return DocumentTypeResponse.builder()
                .code(documentType.getCode())
                .name(documentType.getName())
                .description(documentType.getDescription())
                .source(documentType.getSource())
                .allowedFileGroup(documentType.getAllowedFileGroup())
                .isActive(documentType.getIsActive())
                .isSystem(documentType.getIsSystem())
                .sortOrder(documentType.getSortOrder())
                .build();
    }
}
