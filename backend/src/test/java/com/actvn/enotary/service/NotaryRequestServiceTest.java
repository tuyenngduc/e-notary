package com.actvn.enotary.service;

import com.actvn.enotary.dto.request.ScheduleAppointmentRequest;
import com.actvn.enotary.dto.response.AppointmentResponse;
import com.actvn.enotary.dto.response.DocumentRequirementResponse;
import com.actvn.enotary.entity.Document;
import com.actvn.enotary.entity.DocumentType;
import com.actvn.enotary.entity.NotaryRequest;
import com.actvn.enotary.entity.NotaryServiceType;
import com.actvn.enotary.entity.User;
import com.actvn.enotary.enums.AppointmentStatus;
import com.actvn.enotary.enums.ContractType;
import com.actvn.enotary.enums.Role;
import com.actvn.enotary.enums.RequestStatus;
import com.actvn.enotary.enums.ServiceType;
import com.actvn.enotary.exception.AppException;
import com.actvn.enotary.exception.ErrorCode;
import com.actvn.enotary.repository.AppointmentRepository;
import com.actvn.enotary.repository.ContractTemplateRepository;
import com.actvn.enotary.repository.DocumentRepository;
import com.actvn.enotary.repository.NotaryServiceDocumentRequirementRepository;
import com.actvn.enotary.repository.NotaryServiceTypeRepository;
import com.actvn.enotary.repository.NotaryRequestRepository;
import com.actvn.enotary.repository.UserRepository;
import com.actvn.enotary.repository.VideoSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class NotaryRequestServiceTest {

    @Mock
    NotaryRequestRepository notaryRequestRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    DocumentRepository documentRepository;

    @Mock
    AppointmentRepository appointmentRepository;

     @Mock
     VideoSessionRepository videoSessionRepository;

     @Mock
     ContractTemplateRepository contractTemplateRepository;

     @Mock
     NotaryServiceTypeRepository notaryServiceTypeRepository;

     @Mock
     NotaryServiceDocumentRequirementRepository documentRequirementRepository;

     @Mock
     DocumentTypeService documentTypeService;

     @Mock
     NotificationService notificationService;

     NotaryRequestService service;

     @BeforeEach
     void setUp() {
         service = new NotaryRequestService(
                 notaryRequestRepository,
                 userRepository,
                  documentRepository,
                   appointmentRepository,
                   videoSessionRepository,
                   contractTemplateRepository,
                   notaryServiceTypeRepository,
                   documentRequirementRepository,
                   documentTypeService,
                   notificationService
           );
         lenient().when(documentTypeService.getActiveForUpload(org.mockito.ArgumentMatchers.anyString()))
                 .thenAnswer(invocation -> documentType(invocation.getArgument(0), DocumentTypeService.FILE_GROUP_DOCUMENT, DocumentTypeService.SOURCE_USER_UPLOAD));
         lenient().when(documentTypeService.getByCode(org.mockito.ArgumentMatchers.anyString()))
                 .thenAnswer(invocation -> documentType(invocation.getArgument(0), DocumentTypeService.FILE_GROUP_DOCUMENT, DocumentTypeService.SOURCE_USER_UPLOAD));
      }

     private DocumentType documentType(String code, String fileGroup, String source) {
         DocumentType documentType = new DocumentType();
         documentType.setCode(code);
         documentType.setName(code);
         documentType.setAllowedFileGroup(fileGroup);
         documentType.setSource(source);
         documentType.setIsActive(true);
         return documentType;
     }

     private NotaryServiceType serviceTypeWithoutRequiredTemplate(String serviceCode) {
         NotaryServiceType serviceType = new NotaryServiceType();
         serviceType.setServiceCode(serviceCode);
         serviceType.setRequiresTemplate(false);
         serviceType.setIsActive(true);
         return serviceType;
     }

    @Test
    void acceptRequest_notaryAcceptsWhenRequiredDocumentsArePresent() {
        UUID rid = UUID.randomUUID();

        User notary = new User();
        notary.setUserId(UUID.randomUUID());
        notary.setEmail("notary@example.com");
        notary.setRole(Role.NOTARY);

        NotaryRequest request = new NotaryRequest();
        request.setRequestId(rid);
        request.setStatus(RequestStatus.NEW);
        request.setContractType(ContractType.POWER_OF_ATTORNEY);

        when(userRepository.findByEmail("notary@example.com")).thenReturn(Optional.of(notary));
        when(notaryRequestRepository.findByIdForUpdate(rid)).thenReturn(Optional.of(request));
        when(notaryServiceTypeRepository.findByServiceCode(ContractType.POWER_OF_ATTORNEY.name()))
                .thenReturn(Optional.of(serviceTypeWithoutRequiredTemplate(ContractType.POWER_OF_ATTORNEY.name())));
        when(documentRepository.findDocTypesByRequestId(rid)).thenReturn(List.of(
                "ID_CARD",
                "DRAFT_CONTRACT"
        ));
        when(notaryRequestRepository.save(any(NotaryRequest.class))).thenAnswer(i -> i.getArgument(0));

        NotaryRequest out = service.acceptRequest(rid, "notary@example.com");

        assertEquals(RequestStatus.ACCEPTED, out.getStatus());
        assertEquals(notary.getUserId(), out.getNotary().getUserId());
    }

    @Test
    void acceptRequest_missingRequiredDocuments_returns400() {
        UUID rid = UUID.randomUUID();

        User notary = new User();
        notary.setUserId(UUID.randomUUID());
        notary.setEmail("notary@example.com");
        notary.setRole(Role.NOTARY);

        NotaryRequest request = new NotaryRequest();
        request.setRequestId(rid);
        request.setStatus(RequestStatus.NEW);
        request.setContractType(ContractType.POWER_OF_ATTORNEY);

        when(userRepository.findByEmail("notary@example.com")).thenReturn(Optional.of(notary));
        when(notaryRequestRepository.findByIdForUpdate(rid)).thenReturn(Optional.of(request));
        when(notaryServiceTypeRepository.findByServiceCode(ContractType.POWER_OF_ATTORNEY.name()))
                .thenReturn(Optional.of(serviceTypeWithoutRequiredTemplate(ContractType.POWER_OF_ATTORNEY.name())));
        when(documentRepository.findDocTypesByRequestId(rid)).thenReturn(List.of("ID_CARD"));

        AppException ex = assertThrows(AppException.class, () -> service.acceptRequest(rid, "notary@example.com"));
        assertEquals(400, ex.getStatus().value());
    }

    @Test
    void acceptRequest_alreadyAcceptedByAnotherNotary_returns409() {
        UUID rid = UUID.randomUUID();

        User currentNotary = new User();
        currentNotary.setUserId(UUID.randomUUID());

        NotaryRequest request = new NotaryRequest();
        request.setRequestId(rid);
        request.setStatus(RequestStatus.ACCEPTED);
        request.setNotary(currentNotary);

        User newNotary = new User();
        newNotary.setUserId(UUID.randomUUID());
        newNotary.setEmail("notary@example.com");
        newNotary.setRole(Role.NOTARY);

        when(userRepository.findByEmail("notary@example.com")).thenReturn(Optional.of(newNotary));
        when(notaryRequestRepository.findByIdForUpdate(rid)).thenReturn(Optional.of(request));

        AppException ex = assertThrows(AppException.class, () -> service.acceptRequest(rid, "notary@example.com"));
        assertEquals(409, ex.getStatus().value());
    }

    @Test
    void listForNotaryByStatus_whenStatusNull_returnsProcessingWithoutNotary() {
        UUID notaryId = UUID.randomUUID();
        var pageRequest = PageRequest.of(0, 10);
        var page = new PageImpl<NotaryRequest>(List.of());

        when(notaryRequestRepository.findByStatusAndNotaryIsNull(RequestStatus.PROCESSING, pageRequest)).thenReturn(page);

        var result = service.listForNotaryByStatus(notaryId, null, pageRequest);

        assertSame(page, result);
        verify(notaryRequestRepository).findByStatusAndNotaryIsNull(RequestStatus.PROCESSING, pageRequest);
        verify(notaryRequestRepository, never()).findByStatus(any(), any());
        verify(notaryRequestRepository, never()).findByNotaryUserIdAndStatus(any(), any(), any());
    }

    @Test
    void listForNotaryByStatus_whenStatusNew_returnsAssignedRequestsWithStatus() {
        UUID notaryId = UUID.randomUUID();
        var pageRequest = PageRequest.of(0, 10);
        var page = new PageImpl<NotaryRequest>(List.of());

        when(notaryRequestRepository.findByNotaryUserIdAndStatus(notaryId, RequestStatus.NEW, pageRequest)).thenReturn(page);

        var result = service.listForNotaryByStatus(notaryId, RequestStatus.NEW, pageRequest);

        assertSame(page, result);
        verify(notaryRequestRepository).findByNotaryUserIdAndStatus(notaryId, RequestStatus.NEW, pageRequest);
        verify(notaryRequestRepository, never()).findByStatus(any(), any());
        verify(notaryRequestRepository, never()).findByStatusAndNotaryIsNull(any(), any());
    }

    @Test
    void listAcceptedByNotary_returnsAssignedRequests() {
        UUID notaryId = UUID.randomUUID();
        var pageRequest = PageRequest.of(0, 10);
        var page = new PageImpl<NotaryRequest>(List.of());

        when(notaryRequestRepository.findByNotaryUserId(notaryId, pageRequest)).thenReturn(page);

        var result = service.listAcceptedByNotary(notaryId, pageRequest);

        assertSame(page, result);
        verify(notaryRequestRepository).findByNotaryUserId(eq(notaryId), eq(pageRequest));
    }

    @Test
    void getDocumentRequirements_transferOfProperty_returnsRequiredUploadedAndMissingDocTypes() {
        UUID requestId = UUID.randomUUID();

        NotaryRequest request = new NotaryRequest();
        request.setRequestId(requestId);
        request.setContractType(ContractType.TRANSFER_OF_PROPERTY);

        when(notaryRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(documentRepository.findDocTypesByRequestId(requestId))
                .thenReturn(List.of("ID_CARD", "ID_CARD"));

        DocumentRequirementResponse response = service.getDocumentRequirements(requestId);

        assertEquals(List.of("ID_CARD", "DRAFT_CONTRACT", "PROPERTY_PAPER"), response.getRequiredDocTypes());
        assertEquals(List.of("ID_CARD"), response.getUploadedDocTypes());
        assertEquals(List.of("DRAFT_CONTRACT", "PROPERTY_PAPER"), response.getMissingDocTypes());
        assertFalse(response.isReadyForAccept());
    }

    @Test
    void uploadDocument_terminalStatus_returns409() {
        UUID requestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        User owner = new User();
        owner.setUserId(userId);
        owner.setEmail("client@example.com");

        NotaryRequest request = new NotaryRequest();
        request.setRequestId(requestId);
        request.setClient(owner);
        request.setStatus(RequestStatus.COMPLETED);

        when(notaryRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(userRepository.findByEmail("client@example.com")).thenReturn(Optional.of(owner));

        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain", "data".getBytes());
        AppException ex = assertThrows(AppException.class,
                () -> service.uploadDocument(requestId, "client@example.com", file, "DRAFT_CONTRACT"));

        assertEquals(409, ex.getStatus().value());
        assertEquals(ErrorCode.REQUEST_TERMINAL_STATUS.name(), ex.getCode());
    }

    @Test
    void replaceDocument_terminalStatus_returns409() {
        UUID documentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        User owner = new User();
        owner.setUserId(userId);
        owner.setEmail("client@example.com");

        NotaryRequest request = new NotaryRequest();
        request.setRequestId(UUID.randomUUID());
        request.setClient(owner);
        request.setStatus(RequestStatus.REJECTED);

        Document document = new Document();
        document.setDocumentId(documentId);
        document.setRequest(request);

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(userRepository.findByEmail("client@example.com")).thenReturn(Optional.of(owner));

        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain", "data".getBytes());
        AppException ex = assertThrows(AppException.class,
                () -> service.replaceDocument(documentId, "client@example.com", file));

        assertEquals(409, ex.getStatus().value());
        assertEquals(ErrorCode.REQUEST_TERMINAL_STATUS.name(), ex.getCode());
    }

    @Test
    void replaceDocument_signedDocumentType_notAllowed() {
        UUID documentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        User owner = new User();
        owner.setUserId(userId);
        owner.setEmail("client@example.com");

        NotaryRequest request = new NotaryRequest();
        request.setRequestId(UUID.randomUUID());
        request.setClient(owner);
        request.setStatus(RequestStatus.ACCEPTED);

        Document document = new Document();
        document.setDocumentId(documentId);
        document.setRequest(request);
        document.setDocType("SIGNED_DOCUMENT");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(userRepository.findByEmail("client@example.com")).thenReturn(Optional.of(owner));

        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain", "data".getBytes());
        AppException ex = assertThrows(AppException.class,
                () -> service.replaceDocument(documentId, "client@example.com", file));

        assertEquals(400, ex.getStatus().value());
        assertEquals(ErrorCode.DOCUMENT_REPLACE_NOT_ALLOWED.name(), ex.getCode());
    }

    @Test
    void replaceDocument_sessionVideo_afterSignedDocumentExists_notAllowed() {
        UUID documentId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        User owner = new User();
        owner.setUserId(userId);
        owner.setEmail("client@example.com");

        NotaryRequest request = new NotaryRequest();
        request.setRequestId(requestId);
        request.setClient(owner);
        request.setStatus(RequestStatus.SCHEDULED);

        Document document = new Document();
        document.setDocumentId(documentId);
        document.setRequest(request);
        document.setDocType("SESSION_VIDEO");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(userRepository.findByEmail("client@example.com")).thenReturn(Optional.of(owner));
        when(documentRepository.findDocTypesByRequestId(requestId))
                .thenReturn(List.of("SESSION_VIDEO", "SIGNED_DOCUMENT"));

        MockMultipartFile file = new MockMultipartFile("file", "video.mp4", "video/mp4", "data".getBytes());
        AppException ex = assertThrows(AppException.class,
                () -> service.replaceDocument(documentId, "client@example.com", file));

        assertEquals(400, ex.getStatus().value());
        assertEquals(ErrorCode.DOCUMENT_REPLACE_NOT_ALLOWED.name(), ex.getCode());
    }

    @Test
    void replaceDocument_sessionVideo_beforeSignedDocumentExists_allowed() {
        UUID documentId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        User owner = new User();
        owner.setUserId(userId);
        owner.setEmail("client@example.com");

        NotaryRequest request = new NotaryRequest();
        request.setRequestId(requestId);
        request.setClient(owner);
        request.setStatus(RequestStatus.SCHEDULED);

        Document document = new Document();
        document.setDocumentId(documentId);
        document.setRequest(request);
        document.setDocType("SESSION_VIDEO");
        when(documentTypeService.getActiveForUpload("SESSION_VIDEO"))
                .thenReturn(documentType("SESSION_VIDEO", DocumentTypeService.FILE_GROUP_VIDEO, DocumentTypeService.SOURCE_INTERNAL));

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(userRepository.findByEmail("client@example.com")).thenReturn(Optional.of(owner));
        when(documentRepository.findDocTypesByRequestId(requestId))
                .thenReturn(List.of("SESSION_VIDEO"));
        when(documentRepository.save(any(Document.class))).thenAnswer(i -> i.getArgument(0));

        MockMultipartFile file = new MockMultipartFile("file", "video.mp4", "video/mp4", "data".getBytes());
        Document out = service.replaceDocument(documentId, "client@example.com", file);

        assertEquals(documentId, out.getDocumentId());
        assertNotNull(out.getFileHash());
        assertNotNull(out.getUpdatedAt());
    }

    @Test
    void cancelRequest_ownerCancels_success() {
        UUID rid = UUID.randomUUID();
        UUID uid = UUID.randomUUID();

        User client = new User();
        client.setUserId(uid);
        client.setEmail("a@b.com");

        NotaryRequest r = new NotaryRequest();
        r.setRequestId(rid);
        r.setClient(client);
        r.setStatus(RequestStatus.NEW);

        when(notaryRequestRepository.findById(rid)).thenReturn(Optional.of(r));
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(client));
        when(notaryRequestRepository.save(any(NotaryRequest.class))).thenAnswer(i -> i.getArgument(0));

        NotaryRequest out = service.cancelRequest(rid, "a@b.com");
        assertEquals(RequestStatus.CANCELLED, out.getStatus());
    }

    @Test
    void cancelRequest_forbiddenForNonOwner() {
        UUID rid = UUID.randomUUID();
        User client = new User();
        client.setUserId(UUID.randomUUID());
        client.setEmail("owner@example.com");

        NotaryRequest r = new NotaryRequest();
        r.setRequestId(rid);
        r.setClient(client);
        r.setStatus(RequestStatus.NEW);

        User other = new User();
        other.setUserId(UUID.randomUUID());
        other.setEmail("other@example.com");

        when(notaryRequestRepository.findById(rid)).thenReturn(Optional.of(r));
        when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(other));

        AppException ex = assertThrows(AppException.class, () -> service.cancelRequest(rid, "other@example.com"));
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    void cancelRequest_cannotCancelCompleted() {
        UUID rid = UUID.randomUUID();
        User client = new User();
        client.setUserId(UUID.randomUUID());
        client.setEmail("a@b.com");

        NotaryRequest r = new NotaryRequest();
        r.setRequestId(rid);
        r.setClient(client);
        r.setStatus(RequestStatus.COMPLETED);

        when(notaryRequestRepository.findById(rid)).thenReturn(Optional.of(r));
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(client));

        AppException ex = assertThrows(AppException.class, () -> service.cancelRequest(rid, "a@b.com"));
        assertEquals(400, ex.getStatus().value());
    }

    @Test
    void rejectRequest_assignedNotaryRejects_success() {
        UUID rid = UUID.randomUUID();
        UUID nid = UUID.randomUUID();

        User notary = new User();
        notary.setUserId(nid);
        notary.setEmail("notary@example.com");
        notary.setRole(Role.NOTARY);

        NotaryRequest r = new NotaryRequest();
        r.setRequestId(rid);
        r.setNotary(notary);
        r.setStatus(RequestStatus.ACCEPTED);

        when(notaryRequestRepository.findById(rid)).thenReturn(Optional.of(r));
        when(userRepository.findByEmail("notary@example.com")).thenReturn(Optional.of(notary));
        when(notaryRequestRepository.save(any(NotaryRequest.class))).thenAnswer(i -> i.getArgument(0));

        NotaryRequest out = service.rejectRequest(rid, "notary@example.com", "Thiếu hồ sơ gốc");
        assertEquals(RequestStatus.REJECTED, out.getStatus());
        assertEquals("Thiếu hồ sơ gốc", out.getRejectionReason());
    }

    @Test
    void rejectRequest_forbiddenForUnassignedNotary() {
        UUID rid = UUID.randomUUID();

        User assignedNotary = new User();
        assignedNotary.setUserId(UUID.randomUUID());

        NotaryRequest r = new NotaryRequest();
        r.setRequestId(rid);
        r.setNotary(assignedNotary);
        r.setStatus(RequestStatus.ACCEPTED);

        User anotherNotary = new User();
        anotherNotary.setUserId(UUID.randomUUID());
        anotherNotary.setEmail("another-notary@example.com");
        anotherNotary.setRole(Role.NOTARY);

        when(notaryRequestRepository.findById(rid)).thenReturn(Optional.of(r));
        when(userRepository.findByEmail("another-notary@example.com")).thenReturn(Optional.of(anotherNotary));

        AppException ex = assertThrows(AppException.class,
                () -> service.rejectRequest(rid, "another-notary@example.com", "Không hợp lệ"));
        assertEquals(409, ex.getStatus().value());
    }

    @Test
    void scheduleAppointment_assignedNotarySchedules_offline_success() {
        UUID rid = UUID.randomUUID();
        UUID nid = UUID.randomUUID();

        User notary = new User();
        notary.setUserId(nid);
        notary.setEmail("notary@example.com");
        notary.setRole(Role.NOTARY);

        NotaryRequest r = new NotaryRequest();
        r.setRequestId(rid);
        r.setNotary(notary);
        r.setServiceType(ServiceType.OFFLINE);
        r.setStatus(RequestStatus.ACCEPTED);

        ScheduleAppointmentRequest req = new ScheduleAppointmentRequest();
        req.setScheduledTime(OffsetDateTime.now().plusDays(3));
        req.setPhysicalAddress("VP CN Số 5");

        when(notaryRequestRepository.findById(rid)).thenReturn(Optional.of(r));
        when(userRepository.findByEmail("notary@example.com")).thenReturn(Optional.of(notary));
        when(appointmentRepository.existsByRequestRequestId(rid)).thenReturn(false);
        when(appointmentRepository.save(any())).thenAnswer(i -> {
            com.actvn.enotary.entity.Appointment a = i.getArgument(0);
            a.setAppointmentId(UUID.randomUUID());
            return a;
        });
        when(notaryRequestRepository.save(any(NotaryRequest.class))).thenAnswer(i -> i.getArgument(0));

         AppointmentResponse resp = service.scheduleAppointment(rid, "notary@example.com", req);

         assertEquals(AppointmentStatus.PENDING, resp.getStatus());
         assertEquals("VP CN Số 5", resp.getPhysicalAddress());
         assertNull(resp.getMeetingUrl());
         assertEquals(ServiceType.OFFLINE, resp.getServiceType());
         assertEquals(RequestStatus.SCHEDULED, r.getStatus());
     }

    @Test
    void scheduleAppointment_assignedNotarySchedules_online_noAddress() {
        UUID rid = UUID.randomUUID();
        UUID nid = UUID.randomUUID();

        User notary = new User();
        notary.setUserId(nid);
        notary.setEmail("notary@example.com");
        notary.setRole(Role.NOTARY);

        NotaryRequest r = new NotaryRequest();
        r.setRequestId(rid);
        r.setNotary(notary);
        r.setServiceType(ServiceType.ONLINE);
        r.setStatus(RequestStatus.ACCEPTED);

        ScheduleAppointmentRequest req = new ScheduleAppointmentRequest();
        req.setScheduledTime(OffsetDateTime.now().plusDays(2));

        when(notaryRequestRepository.findById(rid)).thenReturn(Optional.of(r));
        when(userRepository.findByEmail("notary@example.com")).thenReturn(Optional.of(notary));
        when(appointmentRepository.existsByRequestRequestId(rid)).thenReturn(false);
        when(appointmentRepository.save(any())).thenAnswer(i -> {
            com.actvn.enotary.entity.Appointment a = i.getArgument(0);
            a.setAppointmentId(UUID.randomUUID());
            return a;
        });
        when(notaryRequestRepository.save(any(NotaryRequest.class))).thenAnswer(i -> i.getArgument(0));

         AppointmentResponse resp = service.scheduleAppointment(rid, "notary@example.com", req);

         assertNull(resp.getPhysicalAddress());
         assertNotNull(resp.getMeetingUrl());
         assertTrue(resp.getMeetingUrl().contains("/video/room/"));
         assertEquals(ServiceType.ONLINE, resp.getServiceType());
     }

    @Test
    void scheduleAppointment_wrongStatus_returns400() {
        UUID rid = UUID.randomUUID();
        UUID nid = UUID.randomUUID();

        User notary = new User();
        notary.setUserId(nid);
        notary.setEmail("notary@example.com");
        notary.setRole(Role.NOTARY);

        NotaryRequest r = new NotaryRequest();
        r.setRequestId(rid);
        r.setNotary(notary);
        r.setStatus(RequestStatus.NEW); // not ACCEPTED

        ScheduleAppointmentRequest req = new ScheduleAppointmentRequest();
        req.setScheduledTime(OffsetDateTime.now().plusDays(1));

        when(notaryRequestRepository.findById(rid)).thenReturn(Optional.of(r));
        when(userRepository.findByEmail("notary@example.com")).thenReturn(Optional.of(notary));

        AppException ex = assertThrows(AppException.class,
                () -> service.scheduleAppointment(rid, "notary@example.com", req));
        assertEquals(400, ex.getStatus().value());
    }

    @Test
    void scheduleAppointment_duplicateAppointment_returns409() {
        UUID rid = UUID.randomUUID();
        UUID nid = UUID.randomUUID();

        User notary = new User();
        notary.setUserId(nid);
        notary.setEmail("notary@example.com");
        notary.setRole(Role.NOTARY);

        NotaryRequest r = new NotaryRequest();
        r.setRequestId(rid);
        r.setNotary(notary);
        r.setStatus(RequestStatus.ACCEPTED);

        ScheduleAppointmentRequest req = new ScheduleAppointmentRequest();
        req.setScheduledTime(OffsetDateTime.now().plusDays(1));

        when(notaryRequestRepository.findById(rid)).thenReturn(Optional.of(r));
        when(userRepository.findByEmail("notary@example.com")).thenReturn(Optional.of(notary));
        when(appointmentRepository.existsByRequestRequestId(rid)).thenReturn(true);

        AppException ex = assertThrows(AppException.class,
                () -> service.scheduleAppointment(rid, "notary@example.com", req));
        assertEquals(409, ex.getStatus().value());
    }

    @Test
    void scheduleAppointment_forbiddenForUnassignedNotary() {
        UUID rid = UUID.randomUUID();

        User assignedNotary = new User();
        assignedNotary.setUserId(UUID.randomUUID());

        NotaryRequest r = new NotaryRequest();
        r.setRequestId(rid);
        r.setNotary(assignedNotary);
        r.setStatus(RequestStatus.ACCEPTED);

        User anotherNotary = new User();
        anotherNotary.setUserId(UUID.randomUUID());
        anotherNotary.setEmail("other@example.com");
        anotherNotary.setRole(Role.NOTARY);

        ScheduleAppointmentRequest req = new ScheduleAppointmentRequest();
        req.setScheduledTime(OffsetDateTime.now().plusDays(1));

        when(notaryRequestRepository.findById(rid)).thenReturn(Optional.of(r));
        when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(anotherNotary));

        AppException ex = assertThrows(AppException.class,
                () -> service.scheduleAppointment(rid, "other@example.com", req));
        assertEquals(409, ex.getStatus().value());
    }
}

