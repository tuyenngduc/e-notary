package com.actvn.enotary.dto.response;

import com.actvn.enotary.entity.NotaryServiceType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class NotaryServiceTypeResponse {
    private UUID id;
    private String serviceCode;
    private String name;
    private BigDecimal basePrice;
    private String description;
    private Boolean isActive;
    private Boolean requiresTemplate;

    public static NotaryServiceTypeResponse fromEntity(NotaryServiceType entity) {
        return NotaryServiceTypeResponse.builder()
                .id(entity.getId())
                .serviceCode(entity.getServiceCode())
                .name(entity.getName())
                .basePrice(entity.getBasePrice())
                .description(entity.getDescription())
                .isActive(entity.getIsActive())
                .requiresTemplate(entity.getRequiresTemplate())
                .build();
    }
}
