package com.actvn.enotary.dto.response;

import com.actvn.enotary.entity.NotaryOffice;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class NotaryOfficeResponse {
    private UUID id;
    private String name;
    private String address;
    private String phoneNumber;
    private String workingHours;
    private Boolean isActive;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static NotaryOfficeResponse fromEntity(NotaryOffice office) {
        return NotaryOfficeResponse.builder()
                .id(office.getId())
                .name(office.getName())
                .address(office.getAddress())
                .phoneNumber(office.getPhoneNumber())
                .workingHours(office.getWorkingHours())
                .isActive(office.getIsActive())
                .createdAt(office.getCreatedAt())
                .updatedAt(office.getUpdatedAt())
                .build();
    }
}
