package com.actvn.enotary.service;

import com.actvn.enotary.entity.BlockchainTransaction;
import com.actvn.enotary.entity.Document;
import com.actvn.enotary.entity.NotaryRequest;
import com.actvn.enotary.repository.BlockchainTransactionRepository;
import com.actvn.enotary.repository.SignatureRepository;
import com.actvn.enotary.blockchain.BesuJsonRpcClient;
import com.actvn.enotary.blockchain.BlockchainProperties;
import com.actvn.enotary.blockchain.HybridPqTransactionEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlockchainServiceTest {
    @Mock
    BlockchainTransactionRepository blockchainTransactionRepository;
    @Mock
    SignatureRepository signatureRepository;
    @Mock
    BesuJsonRpcClient besuJsonRpcClient;
    @Mock
    HybridPqTransactionEncoder hybridPqTransactionEncoder;

    BlockchainService blockchainService;
    BlockchainProperties blockchainProperties;

    @BeforeEach
    void setUp() {
        blockchainProperties = new BlockchainProperties();
        blockchainProperties.setContractAddress("0x0000000000000000000000000000000000000001");
        blockchainProperties.setSenderPrivateKey("0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63");
        blockchainProperties.setReceiptPollingAttempts(1);
        blockchainService = new BlockchainService(
                blockchainTransactionRepository,
                signatureRepository,
                blockchainProperties,
                besuJsonRpcClient,
                hybridPqTransactionEncoder
        );
    }

    @Test
    void anchorSignedDocument_createsBesuTransaction() {
        NotaryRequest request = new NotaryRequest();
        request.setRequestId(UUID.randomUUID());

        Document document = new Document();
        document.setDocumentId(UUID.randomUUID());
        document.setFileHash("a".repeat(64));

        when(blockchainTransactionRepository.findByDocument_DocumentId(document.getDocumentId()))
                .thenReturn(Optional.empty());
        when(besuJsonRpcClient.callHexLong(any(), anyList()))
                .thenReturn(7L);
        when(hybridPqTransactionEncoder.encodeSigned(any(), any())).thenReturn("0x05deadbeef");
        when(besuJsonRpcClient.callString("eth_sendRawTransaction", java.util.List.of("0x05deadbeef")))
                .thenReturn("0xtxhash");
        when(besuJsonRpcClient.call("eth_getTransactionReceipt", java.util.List.of("0xtxhash")))
                .thenReturn(new ObjectMapper().createObjectNode()
                        .put("status", "0x1")
                        .put("blockNumber", "0x2a"));
        when(blockchainTransactionRepository.save(any(BlockchainTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BlockchainTransaction transaction = blockchainService.anchorSignedDocument(request, document);

        assertSame(request, transaction.getRequest());
        assertSame(document, transaction.getDocument());
        assertEquals(document.getFileHash(), transaction.getDocumentHash());
        assertEquals(42L, transaction.getBlockNumber());
        assertEquals("CONFIRMED", transaction.getStatus());
        assertEquals("0xtxhash", transaction.getTransactionHash());
        verify(blockchainTransactionRepository).save(any(BlockchainTransaction.class));
    }

    @Test
    void anchorSignedDocument_returnsExistingTransactionForSameDocument() {
        UUID documentId = UUID.randomUUID();
        NotaryRequest request = new NotaryRequest();
        request.setRequestId(UUID.randomUUID());

        Document document = new Document();
        document.setDocumentId(documentId);
        document.setFileHash("b".repeat(64));

        BlockchainTransaction existing = new BlockchainTransaction();
        existing.setTransactionId(UUID.randomUUID());
        existing.setDocument(document);
        existing.setRequest(request);

        when(blockchainTransactionRepository.findByDocument_DocumentId(documentId))
                .thenReturn(Optional.of(existing));

        BlockchainTransaction transaction = blockchainService.anchorSignedDocument(request, document);

        assertSame(existing, transaction);
        verify(blockchainTransactionRepository, never()).save(any(BlockchainTransaction.class));
    }
}
