package com.actvn.enotary.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BlockchainSummaryResponse {
    private String networkName;
    private Long chainId;
    private Long latestBlock;
    private Long totalTransactions;
    private Long confirmedTransactions;
    private Integer totalNodes;
    private Integer activeNodes;
    private String mode;
}
