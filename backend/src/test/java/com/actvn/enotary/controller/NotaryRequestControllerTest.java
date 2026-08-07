package com.actvn.enotary.controller;

import com.actvn.enotary.dto.request.NotaryRequestCreateRequest;
import com.actvn.enotary.dto.request.RejectNotaryRequestRequest;
import com.actvn.enotary.dto.request.ScheduleAppointmentRequest;
import com.actvn.enotary.dto.response.AppointmentResponse;
import com.actvn.enotary.dto.response.DocumentRequirementResponse;
import com.actvn.enotary.entity.Document;
import com.actvn.enotary.entity.NotaryRequest;
import com.actvn.enotary.entity.User;
import com.actvn.enotary.enums.AppointmentStatus;
import com.actvn.enotary.enums.ContractType;
import com.actvn.enotary.enums.RequestStatus;
import com.actvn.enotary.enums.ServiceType;
import com.actvn.enotary.enums.VerificationStatus;
import com.actvn.enotary.exception.AppException;
import com.actvn.enotary.exception.ErrorCode;
import com.actvn.enotary.security.CustomUserDetails;
import com.actvn.enotary.service.NotaryRequestService;
import com.actvn.enotary.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class NotaryRequestControllerTest {

    private MockMvc mockMvc;
    private NotaryRequestService notaryRequestService;
    private UserService userService;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private User clientUser;
    private CustomUserDetails clientDetails;
    private UsernamePasswordAuthenticationToken clientAuth;

    private User notaryUser;
    private CustomUserDetails notaryDetails;
    private UsernamePasswordAuthenticationToken notaryAuth;

    @BeforeEach
    void setUp() {
        notaryRequestService = Mockito.mock(NotaryRequestService.class);
        userService = Mockito.mock(UserService.class);
        NotaryRequestController controller = new NotaryRequestController(notaryRequestService, userService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.actvn.enotary.exception.GlobalExceptionHandler())
                .build();

        clientUser = new User();
        clientUser.setUserId(UUID.randomUUID());
        clientUser.setEmail("client@example.com");
        clientUser.setRole(com.actvn.enotary.enums.Role.CLIENT);

        clientDetails = new CustomUserDetails(clientUser);
        clientAuth = new UsernamePasswordAuthenticationToken(clientDetails, null, clientDetails.getAuthorities());

        notaryUser = new User();
        notaryUser.setUserId(UUID.randomUUID());
        notaryUser.setEmail("notary@example.com");
        notaryUser.setRole(com.actvn.enotary.enums.Role.NOTARY);
        notaryUser.setVerificationStatus(VerificationStatus.VERIFIED);

        notaryDetails = new CustomUserDetails(notaryUser);
        notaryAuth = new UsernamePasswordAuthenticationToken(notaryDetails, null, notaryDetails.getAuthorities());

        // Notary verification is enforced by controller for accept/reject/schedule.
        // Use lenient() to avoid "unnecessary stubbing" errors in tests that don't hit those paths.
        Mockito.lenient().when(userService.getById(eq(notaryUser.getUserId()))).thenReturn(notaryUser);
    }

    @Test
    void createRequest_returnsCreated() throws Exception {
        NotaryRequestCreateRequest req = new NotaryRequestCreateRequest();
        req.setServiceType(ServiceType.ONLINE);
        req.setContractType(ContractType.TRANSFER_OF_PROPERTY);
        req.setDescription("Test create");

        NotaryRequest created = new NotaryRequest();
        created.setRequestId(UUID.randomUUID());
        created.setClient(clientUser);
        created.setServiceType(ServiceType.ONLINE);
        created.setContractType(ContractType.TRANSFER_OF_PROPERTY);
        created.setDescription("Test create");
        created.setStatus(RequestStatus.NEW);
        created.setCreatedAt(OffsetDateTime.now());
        created.setUpdatedAt(OffsetDateTime.now());

        DocumentRequirementResponse documentRequirements = DocumentRequirementResponse.builder()
                .requiredDocTypes(List.of("ID_CARD", "PROPERTY_PAPER", "DRAFT_CONTRACT"))
                .uploadedDocTypes(List.of())
                .missingDocTypes(List.of("ID_CARD", "PROPERTY_PAPER", "DRAFT_CONTRACT"))
                .readyForAccept(false)
                .build();

        when(notaryRequestService.createRequest(eq(clientUser.getEmail()), any(NotaryRequestCreateRequest.class)))
                .thenReturn(created);
        when(notaryRequestService.getDocumentRequirements(created.getRequestId())).thenReturn(documentRequirements);

        mockMvc.perform(post("/api/requests")
                        .principal(clientAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.data.requestId").value(created.getRequestId().toString()))
                .andExpect(jsonPath("$.data.clientId").value(clientUser.getUserId().toString()))
                .andExpect(jsonPath("$.data.serviceType").value("ONLINE"))
                .andExpect(jsonPath("$.data.contractType").value("TRANSFER_OF_PROPERTY"))
                .andExpect(jsonPath("$.data.documentRequirements.requiredDocTypes[0]").value("ID_CARD"));
    }

    @Test
    void getRequest_ownerCanView() throws Exception {
        UUID rid = UUID.randomUUID();
        NotaryRequest r = new NotaryRequest();
        r.setRequestId(rid);
        r.setClient(clientUser);
        r.setServiceType(ServiceType.OFFLINE);
        r.setContractType(ContractType.WILL);
        r.setDescription("desc");
        r.setStatus(RequestStatus.NEW);
        r.setCreatedAt(OffsetDateTime.now());
        r.setUpdatedAt(OffsetDateTime.now());

        DocumentRequirementResponse documentRequirements = DocumentRequirementResponse.builder()
                .requiredDocTypes(List.of("ID_CARD", "DRAFT_CONTRACT"))
                .uploadedDocTypes(List.of("ID_CARD"))
                .missingDocTypes(List.of("DRAFT_CONTRACT"))
                .readyForAccept(false)
                .build();

        when(notaryRequestService.getById(rid)).thenReturn(r);
        when(notaryRequestService.getDocumentRequirements(rid)).thenReturn(documentRequirements);

        mockMvc.perform(get("/api/requests/" + rid)
                        .principal(clientAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestId").value(rid.toString()))
                .andExpect(jsonPath("$.data.clientId").value(clientUser.getUserId().toString()))
                .andExpect(jsonPath("$.data.documentRequirements.missingDocTypes[0]").value("DRAFT_CONTRACT"));
    }

    @Test
    void getRequest_forbiddenForOtherUser() throws Exception {
        UUID rid = UUID.randomUUID();
        NotaryRequest r = new NotaryRequest();
        r.setRequestId(rid);
        // owner is different
        User other = new User();
        other.setUserId(UUID.randomUUID());
        other.setEmail("other@example.com");
        r.setClient(other);

        when(notaryRequestService.getById(rid)).thenReturn(r);

        mockMvc.perform(get("/api/requests/" + rid)
                        .principal(clientAuth))
                .andExpect(status().isForbidden());
    }

    @Test
    void listMyRequests_returnsList() throws Exception {
        UUID rid = UUID.randomUUID();
        NotaryRequest r = new NotaryRequest();
        r.setRequestId(rid);
        r.setClient(clientUser);
        r.setServiceType(ServiceType.OFFLINE);
        r.setContractType(ContractType.WILL);
        r.setDescription("desc");
        r.setStatus(RequestStatus.NEW);
        r.setCreatedAt(OffsetDateTime.now());
        r.setUpdatedAt(OffsetDateTime.now());

        when(notaryRequestService.listForClient(clientUser.getUserId())).thenReturn(List.of(r));

        mockMvc.perform(get("/api/requests/me").principal(clientAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].requestId").value(rid.toString()))
                .andExpect(jsonPath("$.data[0].clientId").value(clientUser.getUserId().toString()));
    }

    @Test
    void uploadDocument_allowsOwner() throws Exception {
        UUID rid = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes());

        Document doc = new Document();
        doc.setDocumentId(UUID.randomUUID());

        when(notaryRequestService.uploadDocument(eq(rid), eq(clientUser.getEmail()), any(), eq("DRAFT_CONTRACT")))
                .thenReturn(doc);

        mockMvc.perform(multipart("/api/requests/" + rid + "/documents")
                        .file(file)
                        .param("docType", "DRAFT_CONTRACT")
                        .principal(clientAuth))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));
    }

    @Test
    void getDocumentRequirements_ownerCanView() throws Exception {
        UUID rid = UUID.randomUUID();
        NotaryRequest r = new NotaryRequest();
        r.setRequestId(rid);
        r.setClient(clientUser);

        DocumentRequirementResponse response = DocumentRequirementResponse.builder()
                .requiredDocTypes(List.of("ID_CARD", "DRAFT_CONTRACT"))
                .uploadedDocTypes(List.of("ID_CARD"))
                .missingDocTypes(List.of("DRAFT_CONTRACT"))
                .readyForAccept(false)
                .build();

        when(notaryRequestService.getById(rid)).thenReturn(r);
        when(notaryRequestService.getDocumentRequirements(rid)).thenReturn(response);

        mockMvc.perform(get("/api/requests/" + rid + "/document-requirements")
                        .principal(clientAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requiredDocTypes[0]").value("ID_CARD"))
                .andExpect(jsonPath("$.data.missingDocTypes[0]").value("DRAFT_CONTRACT"))
                .andExpect(jsonPath("$.data.readyForAccept").value(false));
    }

    @Test
    void getDocumentRequirements_forbiddenForOtherUser() throws Exception {
        UUID rid = UUID.randomUUID();
        NotaryRequest r = new NotaryRequest();
        r.setRequestId(rid);
        User other = new User();
        other.setUserId(UUID.randomUUID());
        other.setEmail("other@example.com");
        r.setClient(other);

        when(notaryRequestService.getById(rid)).thenReturn(r);

        mockMvc.perform(get("/api/requests/" + rid + "/document-requirements")
                        .principal(clientAuth))
                .andExpect(status().isForbidden());
    }

    @Test
    void getRequestDocuments_clientBeforeCompleted_hidesSignedDocument() throws Exception {
        UUID rid = UUID.randomUUID();
        NotaryRequest r = requestForDocuments(rid, RequestStatus.AWAITING_PAYMENT);
        Document idCard = documentForRequest(r, "ID_CARD");
        Document signedDocument = documentForRequest(r, "SIGNED_DOCUMENT");

        when(notaryRequestService.getById(rid)).thenReturn(r);
        when(notaryRequestService.getDocumentsByRequestId(rid)).thenReturn(List.of(idCard, signedDocument));

        mockMvc.perform(get("/api/requests/" + rid + "/documents")
                        .principal(clientAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].docType").value("ID_CARD"))
                .andExpect(jsonPath("$.data[1]").doesNotExist());
    }

    @Test
    void getRequestDocuments_clientAfterCompleted_returnsSignedDocument() throws Exception {
        UUID rid = UUID.randomUUID();
        NotaryRequest r = requestForDocuments(rid, RequestStatus.COMPLETED);
        Document idCard = documentForRequest(r, "ID_CARD");
        Document signedDocument = documentForRequest(r, "SIGNED_DOCUMENT");

        when(notaryRequestService.getById(rid)).thenReturn(r);
        when(notaryRequestService.getDocumentsByRequestId(rid)).thenReturn(List.of(idCard, signedDocument));

        mockMvc.perform(get("/api/requests/" + rid + "/documents")
                        .principal(clientAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].docType").value("ID_CARD"))
                .andExpect(jsonPath("$.data[1].docType").value("SIGNED_DOCUMENT"));
    }

    @Test
    void getRequestDocuments_assignedNotaryBeforeCompleted_returnsSignedDocument() throws Exception {
        UUID rid = UUID.randomUUID();
        NotaryRequest r = requestForDocuments(rid, RequestStatus.AWAITING_PAYMENT);
        Document signedDocument = documentForRequest(r, "SIGNED_DOCUMENT");

        when(notaryRequestService.getById(rid)).thenReturn(r);
        when(notaryRequestService.getDocumentsByRequestId(rid)).thenReturn(List.of(signedDocument));

        mockMvc.perform(get("/api/requests/" + rid + "/documents")
                        .principal(notaryAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].docType").value("SIGNED_DOCUMENT"));
    }

    @Test
    void cancelRequest_ownerCanCancel() throws Exception {
        UUID rid = UUID.randomUUID();
        NotaryRequest r = new NotaryRequest();
        r.setRequestId(rid);
        r.setClient(clientUser);
        r.setStatus(RequestStatus.NEW);

        NotaryRequest updated = new NotaryRequest();
        updated.setRequestId(rid);
        updated.setClient(clientUser);
        updated.setStatus(RequestStatus.CANCELLED);

        when(notaryRequestService.cancelRequest(eq(rid), eq(clientUser.getEmail()))).thenReturn(updated);

        mockMvc.perform(post("/api/requests/" + rid + "/cancel").principal(clientAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void cancelRequest_forbiddenForOtherUser() throws Exception {
        UUID rid = UUID.randomUUID();
        // service will throw AppException with FORBIDDEN
        when(notaryRequestService.cancelRequest(eq(rid), eq(clientUser.getEmail())))
                .thenThrow(new com.actvn.enotary.exception.AppException("Không có quyền hủy yêu cầu này", org.springframework.http.HttpStatus.FORBIDDEN));

        mockMvc.perform(post("/api/requests/" + rid + "/cancel").principal(clientAuth))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelRequest_cannotCancelCompleted() throws Exception {
        UUID rid = UUID.randomUUID();
        when(notaryRequestService.cancelRequest(eq(rid), eq(clientUser.getEmail())))
                .thenThrow(new com.actvn.enotary.exception.AppException("Không thể hủy yêu cầu đã hoàn thành", org.springframework.http.HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/requests/" + rid + "/cancel").principal(clientAuth))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectRequest_notaryCanReject() throws Exception {
        UUID rid = UUID.randomUUID();
        NotaryRequest updated = new NotaryRequest();
        updated.setRequestId(rid);
        updated.setClient(clientUser);
        updated.setNotary(notaryUser);
        updated.setStatus(RequestStatus.REJECTED);
        updated.setRejectionReason("Thiếu giấy tờ bản gốc");

        RejectNotaryRequestRequest req = new RejectNotaryRequestRequest();
        req.setReason("Thiếu giấy tờ bản gốc");

        when(notaryRequestService.rejectRequest(eq(rid), eq(notaryUser.getEmail()), eq(req.getReason())))
                .thenReturn(updated);

        mockMvc.perform(post("/api/requests/" + rid + "/reject")
                        .principal(notaryAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("Thiếu giấy tờ bản gốc"));
    }

    @Test
    void rejectRequest_clientForbidden() throws Exception {
        UUID rid = UUID.randomUUID();
        RejectNotaryRequestRequest req = new RejectNotaryRequestRequest();
        req.setReason("Không đủ điều kiện");

        mockMvc.perform(post("/api/requests/" + rid + "/reject")
                        .principal(clientAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

                    @Test
                    void acceptRequest_notaryCanAccept() throws Exception {
                        UUID rid = UUID.randomUUID();

                        NotaryRequest updated = new NotaryRequest();
                        updated.setRequestId(rid);
                        updated.setClient(clientUser);
                        updated.setNotary(notaryUser);
                        updated.setStatus(RequestStatus.ACCEPTED);

                        when(notaryRequestService.acceptRequest(eq(rid), eq(notaryUser.getEmail()), isNull())).thenReturn(updated);

                        mockMvc.perform(post("/api/requests/" + rid + "/accept")
                                        .principal(notaryAuth))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                                .andExpect(jsonPath("$.data.notaryId").value(notaryUser.getUserId().toString()));
                    }

                    @Test
                    void acceptRequest_clientForbidden() throws Exception {
                        UUID rid = UUID.randomUUID();

                        mockMvc.perform(post("/api/requests/" + rid + "/accept")
                                        .principal(clientAuth))
                                .andExpect(status().isForbidden());
                    }

                                    @Test
                                    void acceptRequest_missingDocuments_returnsCodeAndMissingDocTypes() throws Exception {
                                        UUID rid = UUID.randomUUID();

                                        when(notaryRequestService.acceptRequest(eq(rid), eq(notaryUser.getEmail()), isNull()))
                                                .thenThrow(new AppException(
                                                        ErrorCode.REQUEST_MISSING_REQUIRED_DOCUMENTS,
                                                        Map.of("missingDocTypes", List.of("DRAFT_CONTRACT"))
                                                ));

                                        mockMvc.perform(post("/api/requests/" + rid + "/accept")
                                                        .principal(notaryAuth))
                                                .andExpect(status().isBadRequest())
                                                .andExpect(jsonPath("$.code").value(ErrorCode.REQUEST_MISSING_REQUIRED_DOCUMENTS.name()))
                                                .andExpect(jsonPath("$.details.missingDocTypes[0]").value("DRAFT_CONTRACT"));
                                    }

    @Test
    void filterRequests_newStatus_returnsAllNewRequests() throws Exception {
        UUID rid = UUID.randomUUID();
        NotaryRequest r = new NotaryRequest();
        r.setRequestId(rid);
        r.setClient(clientUser);
        r.setServiceType(ServiceType.OFFLINE);
        r.setContractType(ContractType.WILL);
        r.setDescription("desc");
        r.setStatus(RequestStatus.NEW);
        r.setCreatedAt(OffsetDateTime.now());
        r.setUpdatedAt(OffsetDateTime.now());

        var page = new PageImpl<>(List.of(r), PageRequest.of(0, 10), 1);

        when(notaryRequestService.listForNotaryByStatus(eq(notaryUser.getUserId()), eq(RequestStatus.NEW), any(PageRequest.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/requests/filter")
                        .param("status", "NEW")
                        .param("page", "0")
                        .param("size", "10")
                        .principal(notaryAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].requestId").value(rid.toString()))
                .andExpect(jsonPath("$.data.content[0].clientId").value(clientUser.getUserId().toString()));
    }

    @Test
    void filterRequests_nonNewStatus_returnsAssignedRequestsOnly() throws Exception {
        UUID rid = UUID.randomUUID();
        NotaryRequest r = new NotaryRequest();
        r.setRequestId(rid);
        r.setClient(clientUser);
        r.setNotary(notaryUser);
        r.setServiceType(ServiceType.OFFLINE);
        r.setContractType(ContractType.WILL);
        r.setDescription("desc");
        r.setStatus(RequestStatus.ACCEPTED);
        r.setCreatedAt(OffsetDateTime.now());
        r.setUpdatedAt(OffsetDateTime.now());

        var page = new PageImpl<>(List.of(r), PageRequest.of(0, 10), 1);

        when(notaryRequestService.listForNotaryByStatus(eq(notaryUser.getUserId()), eq(RequestStatus.ACCEPTED), any(PageRequest.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/requests/filter")
                        .param("status", "ACCEPTED")
                        .param("page", "0")
                        .param("size", "10")
                        .principal(notaryAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].requestId").value(rid.toString()))
                .andExpect(jsonPath("$.data.content[0].notaryId").value(notaryUser.getUserId().toString()));
    }

    @Test
    void filterRequests_withoutStatus_returnsVisibleRequests() throws Exception {
        UUID rid = UUID.randomUUID();
        NotaryRequest r = new NotaryRequest();
        r.setRequestId(rid);
        r.setClient(clientUser);
        r.setNotary(notaryUser);
        r.setServiceType(ServiceType.OFFLINE);
        r.setContractType(ContractType.WILL);
        r.setDescription("desc");
        r.setStatus(RequestStatus.PROCESSING);
        r.setCreatedAt(OffsetDateTime.now());
        r.setUpdatedAt(OffsetDateTime.now());

        var page = new PageImpl<>(List.of(r), PageRequest.of(0, 10), 1);

        when(notaryRequestService.listForNotaryByStatus(eq(notaryUser.getUserId()), isNull(), any(PageRequest.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/requests/filter")
                        .param("page", "0")
                        .param("size", "10")
                        .principal(notaryAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].requestId").value(rid.toString()))
                .andExpect(jsonPath("$.data.content[0].notaryId").value(notaryUser.getUserId().toString()));
    }

    @Test
    void listAcceptedRequestsForCurrentNotary_returnsAssignedRequests() throws Exception {
        UUID rid = UUID.randomUUID();
        NotaryRequest r = new NotaryRequest();
        r.setRequestId(rid);
        r.setClient(clientUser);
        r.setNotary(notaryUser);
        r.setServiceType(ServiceType.OFFLINE);
        r.setContractType(ContractType.WILL);
        r.setDescription("accepted");
        r.setStatus(RequestStatus.ACCEPTED);
        r.setCreatedAt(OffsetDateTime.now());
        r.setUpdatedAt(OffsetDateTime.now());

        var page = new PageImpl<>(List.of(r), PageRequest.of(0, 10), 1);

        when(notaryRequestService.listAcceptedByNotary(eq(notaryUser.getUserId()), any(PageRequest.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/requests/me/accepted")
                        .param("page", "0")
                        .param("size", "10")
                        .principal(notaryAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].requestId").value(rid.toString()))
                .andExpect(jsonPath("$.data.content[0].notaryId").value(notaryUser.getUserId().toString()));
    }

    @Test
    void listAcceptedRequestsForCurrentNotary_clientForbidden() throws Exception {
        mockMvc.perform(get("/api/requests/me/accepted")
                        .principal(clientAuth))
                .andExpect(status().isForbidden());
    }

    @Test
    void scheduleAppointment_notaryCanScheduleOffline() throws Exception {
        UUID rid = UUID.randomUUID();
        UUID aid = UUID.randomUUID();
        OffsetDateTime scheduledTime = OffsetDateTime.now().plusDays(3);

        ScheduleAppointmentRequest req = new ScheduleAppointmentRequest();
        req.setScheduledTime(scheduledTime);
        req.setPhysicalAddress("Văn phòng công chứng số 2");

        AppointmentResponse response = AppointmentResponse.builder()
                .appointmentId(aid)
                .requestId(rid)
                .serviceType(ServiceType.OFFLINE)
                .scheduledTime(scheduledTime)
                .physicalAddress("Văn phòng công chứng số 2")
                .meetingUrl(null)
                .status(AppointmentStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .build();

        when(notaryRequestService.scheduleAppointment(eq(rid), eq(notaryUser.getEmail()), any(ScheduleAppointmentRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/requests/" + rid + "/schedule")
                        .principal(notaryAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.data.appointmentId").value(aid.toString()))
                .andExpect(jsonPath("$.data.serviceType").value("OFFLINE"))
                .andExpect(jsonPath("$.data.physicalAddress").value("Văn phòng công chứng số 2"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void scheduleAppointment_notaryCanScheduleOnline() throws Exception {
        UUID rid = UUID.randomUUID();
        UUID aid = UUID.randomUUID();
        OffsetDateTime scheduledTime = OffsetDateTime.now().plusDays(2);

        ScheduleAppointmentRequest req = new ScheduleAppointmentRequest();
        req.setScheduledTime(scheduledTime);

        AppointmentResponse response = AppointmentResponse.builder()
                .appointmentId(aid)
                .requestId(rid)
                .serviceType(ServiceType.ONLINE)
                .scheduledTime(scheduledTime)
                .physicalAddress(null)
                .meetingUrl(null)
                .status(AppointmentStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .build();

        when(notaryRequestService.scheduleAppointment(eq(rid), eq(notaryUser.getEmail()), any(ScheduleAppointmentRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/requests/" + rid + "/schedule")
                        .principal(notaryAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.serviceType").value("ONLINE"))
                .andExpect(jsonPath("$.data.meetingUrl").isEmpty());
    }

    @Test
    void scheduleAppointment_clientForbidden() throws Exception {
        UUID rid = UUID.randomUUID();
        ScheduleAppointmentRequest req = new ScheduleAppointmentRequest();
        req.setScheduledTime(OffsetDateTime.now().plusDays(1));

        mockMvc.perform(post("/api/requests/" + rid + "/schedule")
                        .principal(clientAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void scheduleAppointment_wrongStatus_returns400() throws Exception {
        UUID rid = UUID.randomUUID();
        ScheduleAppointmentRequest req = new ScheduleAppointmentRequest();
        req.setScheduledTime(OffsetDateTime.now().plusDays(1));

        when(notaryRequestService.scheduleAppointment(eq(rid), eq(notaryUser.getEmail()), any(ScheduleAppointmentRequest.class)))
                .thenThrow(new com.actvn.enotary.exception.AppException(
                        "Chỉ có thể lên lịch khi yêu cầu đang ở trạng thái ACCEPTED",
                        org.springframework.http.HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/requests/" + rid + "/schedule")
                        .principal(notaryAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void scheduleAppointment_duplicateAppointment_returns409() throws Exception {
        UUID rid = UUID.randomUUID();
        ScheduleAppointmentRequest req = new ScheduleAppointmentRequest();
        req.setScheduledTime(OffsetDateTime.now().plusDays(1));

        when(notaryRequestService.scheduleAppointment(eq(rid), eq(notaryUser.getEmail()), any(ScheduleAppointmentRequest.class)))
                .thenThrow(new com.actvn.enotary.exception.AppException(
                        "Yêu cầu này đã có lịch hẹn",
                        org.springframework.http.HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/requests/" + rid + "/schedule")
                        .principal(notaryAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void scheduleAppointment_alreadyAssigned_returnsStandardizedConflictCode() throws Exception {
        UUID rid = UUID.randomUUID();
        ScheduleAppointmentRequest req = new ScheduleAppointmentRequest();
        req.setScheduledTime(OffsetDateTime.now().plusDays(1));

        when(notaryRequestService.scheduleAppointment(eq(rid), eq(notaryUser.getEmail()), any(ScheduleAppointmentRequest.class)))
                .thenThrow(new AppException(
                        "Yêu cầu đã được công chứng viên khác tiếp nhận",
                        org.springframework.http.HttpStatus.CONFLICT,
                        null,
                        ErrorCode.REQUEST_ALREADY_ASSIGNED.name()
                ));

        mockMvc.perform(post("/api/requests/" + rid + "/schedule")
                        .principal(notaryAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.REQUEST_ALREADY_ASSIGNED.name()));
    }

    private NotaryRequest requestForDocuments(UUID requestId, RequestStatus status) {
        NotaryRequest request = new NotaryRequest();
        request.setRequestId(requestId);
        request.setClient(clientUser);
        request.setNotary(notaryUser);
        request.setStatus(status);
        request.setServiceType(ServiceType.ONLINE);
        request.setContractType(ContractType.PERSONAL_COMMITMENT);
        request.setCreatedAt(OffsetDateTime.now());
        request.setUpdatedAt(OffsetDateTime.now());
        return request;
    }

    private Document documentForRequest(NotaryRequest request, String docType) {
        Document document = new Document();
        document.setDocumentId(UUID.randomUUID());
        document.setRequest(request);
        document.setDocType(docType);
        document.setFilePath("uploads/" + document.getDocumentId() + ".pdf");
        document.setFileHash("abc123");
        document.setCreatedAt(OffsetDateTime.now());
        return document;
    }

}
