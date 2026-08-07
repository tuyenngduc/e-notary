package com.actvn.enotary.dto.request;

import lombok.Data;

import java.util.UUID;

@Data
public class AcceptNotaryRequestRequest {
    private UUID templateId;
}
