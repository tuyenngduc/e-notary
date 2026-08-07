package com.actvn.enotary.controller;

import com.actvn.enotary.entity.Document;
import com.actvn.enotary.entity.NotaryRequest;
import com.actvn.enotary.entity.User;
import com.actvn.enotary.enums.RequestStatus;
import com.actvn.enotary.security.CustomUserDetails;
import com.actvn.enotary.service.NotaryRequestService;
import com.actvn.enotary.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DocumentControllerTest {

    private MockMvc mockMvc;
    private NotaryRequestService notaryRequestService;
    private DocumentRepository documentRepository;

    private UsernamePasswordAuthenticationToken clientAuth;
    private UsernamePasswordAuthenticationToken notaryAuth;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        notaryRequestService = Mockito.mock(NotaryRequestService.class);
        documentRepository = Mockito.mock(DocumentRepository.class);

        DocumentController controller = new DocumentController(documentRepository, notaryRequestService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.actvn.enotary.exception.GlobalExceptionHandler())
                .build();

        User clientUser = new User();
        clientUser.setUserId(UUID.randomUUID());
        clientUser.setEmail("client@example.com");
        clientUser.setRole(com.actvn.enotary.enums.Role.CLIENT);

        CustomUserDetails clientDetails = new CustomUserDetails(clientUser);
        clientAuth = new UsernamePasswordAuthenticationToken(clientDetails, null, clientDetails.getAuthorities());

        User notaryUser = new User();
        notaryUser.setUserId(UUID.randomUUID());
        notaryUser.setEmail("notary@example.com");
        notaryUser.setRole(com.actvn.enotary.enums.Role.NOTARY);

        CustomUserDetails notaryDetails = new CustomUserDetails(notaryUser);
        notaryAuth = new UsernamePasswordAuthenticationToken(notaryDetails, null, notaryDetails.getAuthorities());
    }

    @Test
    void replaceDocument_returnsOk() throws Exception {
        UUID documentId = UUID.randomUUID();

        Document updated = new Document();
        updated.setDocumentId(documentId);
        updated.setDocType("DRAFT_CONTRACT");
        updated.setFilePath("uploads/new-file.pdf");
        updated.setFileHash("abc123");

        when(notaryRequestService.replaceDocument(eq(documentId), eq("client@example.com"), any()))
                .thenReturn(updated);

        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "data".getBytes());

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/documents/{id}", documentId)
                        .file(file)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .principal(clientAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value(documentId.toString()))
                .andExpect(jsonPath("$.data.docType").value("DRAFT_CONTRACT"));
    }

    @Test
    void replaceDocument_unauthenticated_returns401() throws Exception {
        UUID documentId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "data".getBytes());

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/documents/{id}", documentId)
                        .file(file)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void downloadSignedDocument_forClientBeforeCompleted_returns403() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document document = signedDocument(documentId, RequestStatus.AWAITING_PAYMENT);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/documents/{id}", documentId)
                        .principal(clientAuth))
                .andExpect(status().isForbidden());
    }

    @Test
    void viewSignedDocument_forClientBeforeCompleted_returns403() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document document = signedDocument(documentId, RequestStatus.AWAITING_PAYMENT);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/documents/{id}/view", documentId)
                        .principal(clientAuth))
                .andExpect(status().isForbidden());
    }

    @Test
    void downloadSignedDocument_forClientAfterCompleted_returnsOk() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document document = signedDocument(documentId, RequestStatus.COMPLETED);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        Path signedFile = tempDir.resolve("signed.pdf");
        when(notaryRequestService.resolveStoredFilePath("signed.pdf")).thenReturn(signedFile);
        Files.writeString(signedFile, "signed");

        mockMvc.perform(MockMvcRequestBuilders.get("/api/documents/{id}", documentId)
                        .principal(clientAuth))
                .andExpect(status().isOk());
    }

    @Test
    void viewSignedDocument_forAssignedNotaryBeforeCompleted_returnsOk() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document document = signedDocument(documentId, RequestStatus.AWAITING_PAYMENT);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        Path signedFile = tempDir.resolve("signed.pdf");
        when(notaryRequestService.resolveStoredFilePath("signed.pdf")).thenReturn(signedFile);
        Files.writeString(signedFile, "signed");

        mockMvc.perform(MockMvcRequestBuilders.get("/api/documents/{id}/view", documentId)
                        .principal(notaryAuth))
                .andExpect(status().isOk());
    }

    private Document signedDocument(UUID documentId, RequestStatus status) {
        User client = new User();
        client.setUserId(UUID.randomUUID());
        client.setEmail("client@example.com");
        client.setRole(com.actvn.enotary.enums.Role.CLIENT);

        User notary = new User();
        notary.setUserId(UUID.randomUUID());
        notary.setEmail("notary@example.com");
        notary.setRole(com.actvn.enotary.enums.Role.NOTARY);

        NotaryRequest request = new NotaryRequest();
        request.setRequestId(UUID.randomUUID());
        request.setClient(client);
        request.setNotary(notary);
        request.setStatus(status);

        Document document = new Document();
        document.setDocumentId(documentId);
        document.setRequest(request);
        document.setDocType("SIGNED_DOCUMENT");
        document.setFilePath("signed.pdf");
        document.setOriginalFileName("signed.pdf");
        document.setContentType("application/pdf");
        document.setFileHash("abc123");
        return document;
    }
}
