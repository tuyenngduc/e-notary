package com.actvn.enotary.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class PublicDocumentVerificationResponse {
    private boolean verified;
    private String status;
    private String message;
    private String fileName;
    private Long fileSize;
    private String fileHash;
    private OffsetDateTime checkedAt;

    private UUID requestId;
    private String requestCode;
    private String requestStatus;
    private String contractType;

    private UUID documentId;
    private String documentName;
    private String documentType;
    private Long signedSignatureCount;

    private UUID transactionId;
    private String transactionHash;
    private Long blockNumber;
    private String networkName;
    private Long chainId;
    private String blockchainStatus;
    private String nodeName;
    private OffsetDateTime confirmedAt;
}
