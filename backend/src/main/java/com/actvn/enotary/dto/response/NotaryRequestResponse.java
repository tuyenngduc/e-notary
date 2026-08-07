package com.actvn.enotary.dto.response;

import com.actvn.enotary.entity.NotaryRequest;
import com.actvn.enotary.enums.RequestStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class NotaryRequestResponse {
    private UUID requestId;
    private UUID clientId;
    private UUID notaryId; // may be null
    private String serviceType;
    private String contractType;
    private Boolean requiresTemplate;
    private String description;
    private RequestStatus status;
    private String rejectionReason;
    private String meetingUrl;
    private ContractTemplateResponse selectedTemplate;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<String> documentIds; // just ids for now
    private DocumentRequirementResponse documentRequirements;
    private AppointmentResponse appointment;

    public static NotaryRequestResponse fromEntity(NotaryRequest r) {
        return fromEntity(r, null, null);
    }

    public static NotaryRequestResponse fromEntity(NotaryRequest r, DocumentRequirementResponse documentRequirements) {
        return fromEntity(r, documentRequirements, null);
    }

    public static NotaryRequestResponse fromEntity(NotaryRequest r, DocumentRequirementResponse documentRequirements, String meetingUrl) {
        return fromEntity(r, documentRequirements, meetingUrl, null);
    }

    public static NotaryRequestResponse fromEntity(NotaryRequest r, DocumentRequirementResponse documentRequirements, String meetingUrl, AppointmentResponse appointment) {
        return fromEntity(r, documentRequirements, meetingUrl, appointment, true);
    }

    public static NotaryRequestResponse fromEntity(
            NotaryRequest r,
            DocumentRequirementResponse documentRequirements,
            String meetingUrl,
            AppointmentResponse appointment,
            Boolean requiresTemplate) {
        return NotaryRequestResponse.builder()
                .requestId(r.getRequestId())
                .clientId(r.getClient() != null ? r.getClient().getUserId() : null)
                .notaryId(r.getNotary() != null ? r.getNotary().getUserId() : null)
                .serviceType(r.getServiceType() != null ? r.getServiceType().name() : null)
                .contractType(r.getContractType() != null ? r.getContractType().name() : null)
                .requiresTemplate(requiresTemplate)
                .description(r.getDescription())
                .status(r.getStatus())
                .rejectionReason(r.getRejectionReason())
                .meetingUrl(meetingUrl)
                .selectedTemplate(r.getSelectedTemplate() != null ? ContractTemplateResponse.fromEntity(r.getSelectedTemplate()) : null)
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .documentIds(r.getDocuments() != null ? r.getDocuments().stream().map(d -> d.getDocumentId().toString()).toList() : List.of())
                .documentRequirements(documentRequirements)
                .appointment(appointment)
                .build();
    }
}

