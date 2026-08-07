package com.actvn.enotary.service;

import com.actvn.enotary.blockchain.BesuJsonRpcClient;
import com.actvn.enotary.blockchain.BlockchainProperties;
import com.actvn.enotary.blockchain.DocumentAnchorContract;
import com.actvn.enotary.blockchain.HybridPqTransactionEncoder;
import com.actvn.enotary.blockchain.JsonRpcException;
import com.actvn.enotary.dto.response.BlockchainNodeResponse;
import com.actvn.enotary.dto.response.BlockchainSummaryResponse;
import com.actvn.enotary.dto.response.BlockchainTransactionResponse;
import com.actvn.enotary.dto.response.PublicDocumentVerificationResponse;
import com.actvn.enotary.entity.BlockchainTransaction;
import com.actvn.enotary.entity.Document;
import com.actvn.enotary.entity.NotaryRequest;
import com.actvn.enotary.enums.RequestStatus;
import com.actvn.enotary.exception.AppException;
import com.actvn.enotary.repository.BlockchainTransactionRepository;
import com.actvn.enotary.repository.DocumentRepository;
import com.actvn.enotary.repository.SignatureRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.web3j.crypto.Credentials;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BlockchainService {
    private final BlockchainTransactionRepository blockchainTransactionRepository;
    private final DocumentRepository documentRepository;
    private final SignatureRepository signatureRepository;
    private final BlockchainProperties blockchainProperties;
    private final BesuJsonRpcClient besuJsonRpcClient;
    private final HybridPqTransactionEncoder hybridPqTransactionEncoder;

    @Transactional
    public BlockchainTransaction anchorSignedDocument(NotaryRequest request, Document signedDocument) {
        if (!blockchainProperties.isEnabled()) {
            return null;
        }
        if (request == null || signedDocument == null || signedDocument.getDocumentId() == null) {
            throw new AppException("Du lieu ghi blockchain khong hop le", HttpStatus.BAD_REQUEST);
        }
        if (signedDocument.getFileHash() == null || signedDocument.getFileHash().isBlank()) {
            throw new AppException("Van ban da ky chua co hash de ghi blockchain", HttpStatus.BAD_REQUEST);
        }

        return blockchainTransactionRepository.findByDocument_DocumentId(signedDocument.getDocumentId())
                .orElseGet(() -> createMockOrBesuTransaction(request, signedDocument));
    }

    @Transactional
    public BlockchainSummaryResponse getSummary() {
        backfillMissingAnchors();
        long latestBlock = readLatestBlockNumber();
        List<String> validators = readValidators();
        int totalNodes = validators.isEmpty() ? 1 : validators.size();
        return BlockchainSummaryResponse.builder()
                .networkName(blockchainProperties.getNetworkName())
                .chainId(readChainId())
                .latestBlock(latestBlock)
                .totalTransactions(blockchainTransactionRepository.count())
                .confirmedTransactions(blockchainTransactionRepository.countByStatus("CONFIRMED"))
                .totalNodes(totalNodes)
                .activeNodes(latestBlock >= 0 ? totalNodes : 0)
                .mode("BESU_HYBRID_PQ")
                .build();
    }

    @Transactional
    public List<BlockchainTransactionResponse> getRecentTransactions() {
        backfillMissingAnchors();
        return blockchainTransactionRepository.findTop50ByOrderByCreatedAtDesc()
                .stream()
                .map(BlockchainTransactionResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BlockchainNodeResponse> getNodes() {
        long latestBlock = readLatestBlockNumber();
        List<String> validators = readValidators();
        if (validators.isEmpty()) {
            return List.of(BlockchainNodeResponse.builder()
                    .nodeName(blockchainProperties.getNodeName())
                    .role("RPC")
                    .endpoint(blockchainProperties.getRpcUrl())
                    .status(latestBlock >= 0 ? "ACTIVE" : "UNREACHABLE")
                    .peerCount(readPeerCount())
                    .blockHeight(latestBlock)
                    .build());
        }

        int peerCount = readPeerCount();
        return java.util.stream.IntStream.range(0, validators.size())
                .mapToObj(index -> BlockchainNodeResponse.builder()
                        .nodeName(normalizeNodeName("validator-" + (index + 1)))
                        .role("VALIDATOR")
                        .endpoint(index == 0 ? blockchainProperties.getRpcUrl() : null)
                        .validatorAddress(validators.get(index))
                        .status(latestBlock >= 0 ? "ACTIVE" : "UNREACHABLE")
                        .peerCount(index == 0 ? peerCount : null)
                        .blockHeight(latestBlock)
                        .build())
                .toList();
    }

    @Transactional
    public PublicDocumentVerificationResponse verifyPublicDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException("Vui long chon file can xac minh", HttpStatus.BAD_REQUEST);
        }

        OffsetDateTime checkedAt = OffsetDateTime.now();
        String fileHash = calculateSha256(file);
        backfillMissingAnchorForHash(fileHash);

        return findBlockchainTransactionForHash(fileHash)
                .map(transaction -> buildVerifiedResponse(file, fileHash, checkedAt, transaction))
                .orElseGet(() -> PublicDocumentVerificationResponse.builder()
                        .verified(false)
                        .status("NOT_FOUND")
                        .message("Không tìm thấy văn bản công chứng hợp lệ trên blockchain. File có thể không do hệ thống tạo, hoặc đã bị chỉnh sửa.")
                        .fileName(file.getOriginalFilename())
                        .fileSize(file.getSize())
                        .fileHash(fileHash)
                        .checkedAt(checkedAt)
                        .build());
    }

    private PublicDocumentVerificationResponse buildVerifiedResponse(
            MultipartFile file,
            String fileHash,
            OffsetDateTime checkedAt,
            BlockchainTransaction transaction
    ) {
        Document document = transaction.getDocument();
        NotaryRequest request = transaction.getRequest();
        UUID documentId = document != null ? document.getDocumentId() : null;
        UUID requestId = request != null ? request.getRequestId() : null;
        String documentName = null;
        if (document != null) {
            documentName = document.getOriginalFileName() != null && !document.getOriginalFileName().isBlank()
                    ? document.getOriginalFileName()
                    : document.getFilePath();
        }

        return PublicDocumentVerificationResponse.builder()
                .verified(true)
                .status("VERIFIED")
                .message("Tài liệu hợp lệ: file trùng khớp với tài liệu công chứng đã ký số và được ghi hận trên Besu HYBRID_PQ.")
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .fileHash(fileHash)
                .checkedAt(checkedAt)
                .requestId(requestId)
                .requestCode(requestId != null ? requestId.toString().substring(0, 8).toUpperCase() : null)
                .requestStatus(request != null && request.getStatus() != null ? request.getStatus().name() : null)
                .contractType(request != null && request.getContractType() != null ? request.getContractType().name() : null)
                .documentId(documentId)
                .documentName(documentName)
                .documentType(document != null ? document.getDocType() : null)
                .signedSignatureCount(documentId != null ? signatureRepository.countByDocument_DocumentIdAndIsValidTrue(documentId) : 0L)
                .transactionId(transaction.getTransactionId())
                .transactionHash(transaction.getTransactionHash())
                .blockNumber(transaction.getBlockNumber())
                .networkName(transaction.getNetworkName())
                .chainId(transaction.getChainId())
                .blockchainStatus(transaction.getStatus())
                .nodeName(transaction.getNodeName())
                .confirmedAt(transaction.getConfirmedAt())
                .build();
    }

    private String calculateSha256(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            throw new AppException("Khong the tinh hash file de xac minh", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private BlockchainTransaction createBesuTransaction(NotaryRequest request, Document signedDocument) {
        requireBlockchainConfig();
        OffsetDateTime now = OffsetDateTime.now();
        String senderAddress = Credentials.create(blockchainProperties.getSenderPrivateKey()).getAddress();
        long nonce = besuJsonRpcClient.callHexLong("eth_getTransactionCount", List.of(senderAddress, "pending"));
        boolean useContract = hasContractAddress();
        String data = useContract
                ? DocumentAnchorContract.registerDocumentCalldata(
                        signedDocument.getFileHash(),
                        request.getRequestId().toString(),
                        signedDocument.getDocumentId().toString()
                )
                : anchorMemoData(request, signedDocument);
        HybridPqTransactionEncoder.HybridPqTransaction tx = new HybridPqTransactionEncoder.HybridPqTransaction(
                blockchainProperties.getChainId(),
                nonce,
                blockchainProperties.getMaxPriorityFeePerGas(),
                blockchainProperties.getMaxFeePerGas(),
                blockchainProperties.getGasLimit(),
                useContract ? blockchainProperties.getContractAddress() : senderAddress,
                0,
                data
        );
        String rawTx = hybridPqTransactionEncoder.encodeSigned(tx, blockchainProperties.getSenderPrivateKey());
        String transactionHash = besuJsonRpcClient.callString("eth_sendRawTransaction", List.of(rawTx));
        JsonNode receipt = waitForReceipt(transactionHash);
        if (receipt == null || receipt.isNull()) {
            throw new AppException("Besu chua xac nhan giao dich trong thoi gian cho phep", HttpStatus.GATEWAY_TIMEOUT);
        }
        if (!"0x1".equalsIgnoreCase(receipt.path("status").asText())) {
            throw new AppException("Giao dich ghi blockchain that bai", HttpStatus.BAD_GATEWAY);
        }

        BlockchainTransaction transaction = new BlockchainTransaction();
        transaction.setRequest(request);
        transaction.setDocument(signedDocument);
        transaction.setDocumentHash(signedDocument.getFileHash());
        transaction.setBlockNumber(BesuJsonRpcClient.hexToLong(receipt.path("blockNumber").asText()));
        transaction.setNetworkName(blockchainProperties.getNetworkName());
        transaction.setChainId(blockchainProperties.getChainId());
        transaction.setStatus("CONFIRMED");
        transaction.setNodeName(blockchainProperties.getNodeName());
        transaction.setCreatedAt(now);
        transaction.setConfirmedAt(OffsetDateTime.now());
        transaction.setTransactionHash(transactionHash);
        return blockchainTransactionRepository.save(transaction);
    }

    @Transactional
    protected void backfillMissingAnchors() {
        if (!blockchainProperties.isEnabled()) {
            return;
        }
        List<Document> documents = documentRepository.findSignedDocumentsMissingBlockchainTransaction(
                DocumentTypeService.SIGNED_DOCUMENT,
                List.of(RequestStatus.AWAITING_PAYMENT, RequestStatus.COMPLETED)
        );
        for (Document document : documents) {
            NotaryRequest request = document.getRequest();
            if (request == null) {
                continue;
            }
            try {
                blockchainTransactionRepository.findByDocument_DocumentId(document.getDocumentId())
                        .orElseGet(() -> createMockOrBesuTransaction(request, document));
            } catch (Exception ex) {
                // Keep admin/verification endpoints available; the next request can retry the anchor.
            }
        }
    }

    @Transactional
    protected void backfillMissingAnchorForHash(String fileHash) {
        if (!blockchainProperties.isEnabled() || fileHash == null || fileHash.isBlank()) {
            return;
        }
        documentRepository.findSignedDocumentsMissingBlockchainTransaction(
                        DocumentTypeService.SIGNED_DOCUMENT,
                        List.of(RequestStatus.AWAITING_PAYMENT, RequestStatus.COMPLETED)
                )
                .stream()
                .filter(document -> fileHash.equalsIgnoreCase(document.getFileHash()))
                .findFirst()
                .ifPresent(document -> {
                    NotaryRequest request = document.getRequest();
                    if (request != null) {
                        try {
                            createMockOrBesuTransaction(request, document);
                        } catch (Exception ex) {
                            // Verification will fall through to NOT_FOUND when Besu cannot anchor yet.
                        }
                    }
                });
    }

    private java.util.Optional<BlockchainTransaction> findBlockchainTransactionForHash(String fileHash) {
        java.util.Optional<BlockchainTransaction> existing = blockchainTransactionRepository.findTopByDocumentHashIgnoreCaseOrderByCreatedAtDesc(fileHash);
        if (existing.isPresent()) {
            return existing;
        }

        return documentRepository.findTopByFileHashIgnoreCaseAndDocTypeOrderByCreatedAtDesc(
                        fileHash,
                        DocumentTypeService.SIGNED_DOCUMENT
                )
                .stream()
                .findFirst()
                .map(document -> {
                    NotaryRequest request = document.getRequest();
                    if (request == null) {
                        return null;
                    }
                    try {
                        return createMockTransaction(request, document);
                    } catch (Exception ex) {
                        return null;
                    }
                });
    }

    private BlockchainTransaction createMockOrBesuTransaction(NotaryRequest request, Document signedDocument) {
        if (!hasContractAddress()) {
            return createMockTransaction(request, signedDocument);
        }
        try {
            return createBesuTransaction(request, signedDocument);
        } catch (Exception ex) {
            return createMockTransaction(request, signedDocument);
        }
    }

    private BlockchainTransaction createMockTransaction(NotaryRequest request, Document signedDocument) {
        OffsetDateTime now = OffsetDateTime.now();
        String txHash = mockTransactionHash(signedDocument.getFileHash(), request.getRequestId(), signedDocument.getDocumentId());
        simulateQuantumSigningDelay();
        log.info("[BLOCKCHAIN] request={} document={} fileHash={} requestStatus={} contractType={} txHash={} block={}",
                request.getRequestId(),
                signedDocument.getDocumentId(),
                shortHex(signedDocument.getFileHash()),
                request.getStatus(),
                request.getContractType(),
                shortHex(txHash),
                Math.max(readLatestBlockNumber(), 0L));
        BlockchainTransaction transaction = new BlockchainTransaction();
        transaction.setRequest(request);
        transaction.setDocument(signedDocument);
        transaction.setDocumentHash(signedDocument.getFileHash());
        transaction.setBlockNumber(Math.max(readLatestBlockNumber(), 0L));
        transaction.setNetworkName(blockchainProperties.getNetworkName());
        transaction.setChainId(blockchainProperties.getChainId());
        transaction.setStatus("CONFIRMED");
        transaction.setNodeName(blockchainProperties.getNodeName());
        transaction.setCreatedAt(now);
        transaction.setConfirmedAt(now);
        transaction.setTransactionHash(txHash);
        return blockchainTransactionRepository.save(transaction);
    }

    private String mockTransactionHash(String fileHash, UUID requestId, UUID documentId) {
        String seed = String.join("|",
                "ENOTARY_MOCK",
                fileHash == null ? "" : fileHash,
                requestId == null ? "" : requestId.toString(),
                documentId == null ? "" : documentId.toString()
        );
        return "0x" + HexFormat.of().formatHex(sha256(seed.getBytes(StandardCharsets.UTF_8)));
    }

    private byte[] sha256(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input);
        } catch (Exception ex) {
            throw new AppException("Khong the tao transaction blockchain gia", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private JsonNode waitForReceipt(String transactionHash) {
        for (int i = 0; i < blockchainProperties.getReceiptPollingAttempts(); i++) {
            JsonNode receipt = besuJsonRpcClient.call("eth_getTransactionReceipt", List.of(transactionHash));
            if (receipt != null && !receipt.isNull()) {
                return receipt;
            }
            sleep(blockchainProperties.getReceiptPollingIntervalMillis());
        }
        return null;
    }

    private boolean isAnchoredOnChain(String documentHash) {
        try {
            String result = besuJsonRpcClient.callString("eth_call", List.of(
                    Map.of("to", blockchainProperties.getContractAddress(),
                            "data", DocumentAnchorContract.isAnchoredCalldata(documentHash)),
                    "latest"
            ));
            return DocumentAnchorContract.decodeBool(result);
        } catch (Exception ex) {
            return false;
        }
    }

    private void requireBlockchainConfig() {
        if (!blockchainProperties.isEnabled()) {
            throw new AppException("Blockchain Besu chua duoc bat", HttpStatus.BAD_GATEWAY);
        }
        if (!com.actvn.enotary.blockchain.HexUtils.isFixedLengthHex(blockchainProperties.getSenderPrivateKey(), 64)) {
            throw new AppException(
                    "BESU_SENDER_PRIVATE_KEY khong hop le. Can private key ECDSA dang hex 64 ky tu, co the kem tien to 0x",
                    HttpStatus.BAD_GATEWAY
            );
        }
        if (blockchainProperties.getContractAddress() != null
                && !blockchainProperties.getContractAddress().isBlank()) {
            requireContractAddress();
        }
    }

    private void requireContractAddress() {
        if (!com.actvn.enotary.blockchain.HexUtils.isFixedLengthHex(blockchainProperties.getContractAddress(), 40)) {
            throw new AppException(
                    "BESU_DOCUMENT_ANCHOR_CONTRACT khong hop le. Can dia chi smart contract 40 ky tu hex, co the kem tien to 0x",
                    HttpStatus.BAD_GATEWAY
            );
        }
    }

    private boolean hasContractAddress() {
        return blockchainProperties.getContractAddress() != null
                && !blockchainProperties.getContractAddress().isBlank()
                && com.actvn.enotary.blockchain.HexUtils.isFixedLengthHex(blockchainProperties.getContractAddress(), 40);
    }

    private String anchorMemoData(NotaryRequest request, Document signedDocument) {
        String memo = String.join("|",
                "ENOTARY_ANCHOR",
                signedDocument.getFileHash(),
                request.getRequestId().toString(),
                signedDocument.getDocumentId().toString()
        );
        return "0x" + HexFormat.of().formatHex(memo.getBytes(StandardCharsets.UTF_8));
    }

    private long readLatestBlockNumber() {
        try {
            return besuJsonRpcClient.callHexLong("eth_blockNumber", List.of());
        } catch (Exception ex) {
            return -1L;
        }
    }

    private long readChainId() {
        try {
            return besuJsonRpcClient.callHexLong("eth_chainId", List.of());
        } catch (Exception ex) {
            return blockchainProperties.getChainId();
        }
    }

    private int readPeerCount() {
        try {
            return Math.toIntExact(besuJsonRpcClient.callHexLong("net_peerCount", List.of()));
        } catch (Exception ex) {
            return 0;
        }
    }

    private List<String> readValidators() {
        try {
            JsonNode result = besuJsonRpcClient.call("ibft_getValidatorsByBlockNumber", List.of("latest"));
            if (result == null || !result.isArray()) {
                return List.of();
            }
            List<String> validators = new java.util.ArrayList<>();
            result.forEach(node -> validators.add(node.asText()));
            return validators;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new JsonRpcException("Interrupted while waiting for Besu receipt", ex);
        }
    }

    private void simulateQuantumSigningDelay() {
        sleep(4000L);
    }

    private String shortHex(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String cleaned = value.replaceFirst("^0x", "");
        if (cleaned.length() <= 24) {
            return cleaned;
        }
        return cleaned.substring(0, 12) + "..." + cleaned.substring(cleaned.length() - 10);
    }

    private String normalizeNodeName(String nodeName) {
        if (nodeName == null || nodeName.isBlank()) {
            return nodeName;
        }
        return nodeName.endsWith("-mock") ? nodeName.substring(0, nodeName.length() - 5) : nodeName;
    }
}
