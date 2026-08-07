package com.actvn.enotary.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BlockchainNodeResponse {
    private String nodeName;
    private String role;
    private String endpoint;
    private String validatorAddress;
    private String status;
    private Integer peerCount;
    private Long blockHeight;
}
