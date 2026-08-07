package com.actvn.enotary.dto.response;

import com.actvn.enotary.entity.BlockchainTransaction;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class BlockchainTransactionResponse {
    private UUID transactionId;
    private UUID requestId;
    private UUID documentId;
    private String requestCode;
    private String documentHash;
    private String transactionHash;
    private Long blockNumber;
    private String networkName;
    private Long chainId;
    private String status;
    private String nodeName;
    private OffsetDateTime createdAt;
    private OffsetDateTime confirmedAt;

    public static BlockchainTransactionResponse fromEntity(BlockchainTransaction transaction) {
        UUID requestId = transaction.getRequest() != null ? transaction.getRequest().getRequestId() : null;
        return BlockchainTransactionResponse.builder()
                .transactionId(transaction.getTransactionId())
                .requestId(requestId)
                .documentId(transaction.getDocument() != null ? transaction.getDocument().getDocumentId() : null)
                .requestCode(requestId != null ? requestId.toString().substring(0, 8).toUpperCase() : null)
                .documentHash(transaction.getDocumentHash())
                .transactionHash(transaction.getTransactionHash())
                .blockNumber(transaction.getBlockNumber())
                .networkName(transaction.getNetworkName())
                .chainId(transaction.getChainId())
                .status(transaction.getStatus())
                .nodeName(normalizeNodeName(transaction.getNodeName()))
                .createdAt(transaction.getCreatedAt())
                .confirmedAt(transaction.getConfirmedAt())
                .build();
    }

    private static String normalizeNodeName(String nodeName) {
        if (nodeName == null || nodeName.isBlank()) {
            return nodeName;
        }
        return nodeName.endsWith("-mock") ? nodeName.substring(0, nodeName.length() - 5) : nodeName;
    }
}
