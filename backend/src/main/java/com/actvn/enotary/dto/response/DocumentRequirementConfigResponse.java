package com.actvn.enotary.dto.response;

import com.actvn.enotary.entity.NotaryServiceType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class DocumentRequirementConfigResponse {
    private UUID serviceId;
    private String serviceCode;
    private String serviceName;
    private String description;
    private BigDecimal basePrice;
    private Boolean isActive;
    private Boolean requiresTemplate;
    private List<String> requiredDocTypes;

    public static DocumentRequirementConfigResponse fromEntity(NotaryServiceType serviceType, List<String> requiredDocTypes) {
        return DocumentRequirementConfigResponse.builder()
                .serviceId(serviceType.getId())
                .serviceCode(serviceType.getServiceCode())
                .serviceName(serviceType.getName())
                .description(serviceType.getDescription())
                .basePrice(serviceType.getBasePrice())
                .isActive(serviceType.getIsActive())
                .requiresTemplate(serviceType.getRequiresTemplate())
                .requiredDocTypes(requiredDocTypes)
                .build();
    }
}
