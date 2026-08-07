package com.actvn.enotary.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DocumentRequirementResponse {
    private List<String> requiredDocTypes;
    private List<String> uploadedDocTypes;
    private List<String> missingDocTypes;
    private List<RequiredDocumentResponse> requiredDocuments;
    private boolean readyForAccept;
}

