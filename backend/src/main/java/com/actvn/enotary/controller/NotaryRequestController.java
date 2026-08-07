package com.actvn.enotary.controller;

import com.actvn.enotary.dto.request.AcceptNotaryRequestRequest;
import com.actvn.enotary.dto.request.NotaryRequestCreateRequest;
import com.actvn.enotary.dto.request.RejectNotaryRequestRequest;
import com.actvn.enotary.dto.request.ScheduleAppointmentRequest;
import com.actvn.enotary.dto.response.ApiResponse;
import com.actvn.enotary.dto.response.ApiResponseUtil;
import com.actvn.enotary.dto.response.AppointmentResponse;
import com.actvn.enotary.dto.response.DocumentRequirementResponse;
import com.actvn.enotary.dto.response.DocumentResponse;
import com.actvn.enotary.dto.response.NotaryRequestResponse;
import com.actvn.enotary.entity.Document;
import com.actvn.enotary.entity.NotaryRequest;
import com.actvn.enotary.enums.RequestStatus;
import com.actvn.enotary.enums.VerificationStatus;
import com.actvn.enotary.exception.AppException;
import com.actvn.enotary.exception.ErrorCode;
import com.actvn.enotary.security.CustomUserDetails;
import com.actvn.enotary.service.DocumentTypeService;
import com.actvn.enotary.service.NotaryRequestService;
import com.actvn.enotary.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
public class NotaryRequestController {

    private final NotaryRequestService notaryRequestService;
    private final UserService userService;

    private void assertNotaryVerified(CustomUserDetails userDetails) {
        if (userDetails.getRole() == null || !"NOTARY".equals(userDetails.getRole().name())) {
            return;
        }

        VerificationStatus status = userService.getById(userDetails.getId()).getVerificationStatus();
        if (status != VerificationStatus.VERIFIED) {
            throw new AppException("Tài khoản công chứng viên chưa được xác thực", HttpStatus.FORBIDDEN);
        }
    }

    private NotaryRequestResponse toResponse(NotaryRequest request) {
        AppointmentResponse appointment = notaryRequestService.getAppointmentResponseByRequestId(request.getRequestId()).orElse(null);
        String meetingUrl = shouldExposeMeetingUrl(request, appointment) ? appointment.getMeetingUrl() : null;
        return NotaryRequestResponse.fromEntity(request, null, meetingUrl, appointment, notaryRequestService.requiresTemplate(request));
    }

    private NotaryRequestResponse toResponse(NotaryRequest request, DocumentRequirementResponse requirements) {
        AppointmentResponse appointment = notaryRequestService.getAppointmentResponseByRequestId(request.getRequestId()).orElse(null);
        String meetingUrl = shouldExposeMeetingUrl(request, appointment) ? appointment.getMeetingUrl() : null;
        return NotaryRequestResponse.fromEntity(request, requirements, meetingUrl, appointment, notaryRequestService.requiresTemplate(request));
    }

    private boolean shouldExposeMeetingUrl(NotaryRequest request, AppointmentResponse appointment) {
        return appointment != null
                && appointment.getMeetingUrl() != null
                && appointment.getStatus() == com.actvn.enotary.enums.AppointmentStatus.PENDING
                && (request.getStatus() == RequestStatus.SCHEDULED || request.getStatus() == RequestStatus.IN_VIDEO_CALL);
    }

    private boolean canAccessRequestForRead(CustomUserDetails userDetails, String email, NotaryRequest request) {
        boolean isOwner = request.getClient() != null && request.getClient().getEmail().equals(email);
        boolean isAssignedNotary = request.getNotary() != null && request.getNotary().getEmail().equals(email);
        boolean isAdmin = userDetails.getRole() != null && "ADMIN".equals(userDetails.getRole().name());
        boolean isNotary = userDetails.getRole() != null && "NOTARY".equals(userDetails.getRole().name());

        // Notary can inspect PROCESSING requests before accepting; other statuses require assignment.
        boolean canInspectProcessingRequest = isNotary && request.getStatus() == RequestStatus.PROCESSING;

        return isOwner || isAssignedNotary || isAdmin || canInspectProcessingRequest;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NotaryRequestResponse>> createRequest(
            Authentication authentication,
            @Valid @RequestBody NotaryRequestCreateRequest request) {

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new AppException(ErrorCode.INVALID_AUTHENTICATION);
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String email = userDetails.getUsername();

        NotaryRequest created = notaryRequestService.createRequest(email, request);
        DocumentRequirementResponse documentRequirements = notaryRequestService.getDocumentRequirements(created.getRequestId());
        URI location = URI.create("/api/requests/" + created.getRequestId());
        return ResponseEntity.created(location).body(
                ApiResponseUtil.created(toResponse(created, documentRequirements), "Tạo yêu cầu công chứng thành công")
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NotaryRequestResponse>> getRequest(
            Authentication authentication,
            @PathVariable("id") UUID id) {

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new AppException(ErrorCode.INVALID_AUTHENTICATION);
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String email = userDetails.getUsername();

        NotaryRequest r = notaryRequestService.getById(id);

        if (!canAccessRequestForRead(userDetails, email, r)) {
            throw new AppException(ErrorCode.INVALID_AUTHORIZATION);
        }

        DocumentRequirementResponse documentRequirements = notaryRequestService.getDocumentRequirements(id);
        return ResponseEntity.ok(ApiResponseUtil.success(toResponse(r, documentRequirements)));
    }

    @GetMapping("/{id}/document-requirements")
    public ResponseEntity<ApiResponse<DocumentRequirementResponse>> getDocumentRequirements(
            Authentication authentication,
            @PathVariable("id") UUID id) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new AppException(ErrorCode.INVALID_AUTHENTICATION);
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String email = userDetails.getUsername();

        NotaryRequest r = notaryRequestService.getById(id);

        if (!canAccessRequestForRead(userDetails, email, r)) {
            throw new AppException(ErrorCode.INVALID_AUTHORIZATION);
        }

        return ResponseEntity.ok(ApiResponseUtil.success(notaryRequestService.getDocumentRequirements(id)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<NotaryRequestResponse>>> listMyRequests(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new AppException(ErrorCode.INVALID_AUTHENTICATION);
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        UUID userId = userDetails.getId();

        List<NotaryRequest> list = notaryRequestService.listForClient(userId);
        List<NotaryRequestResponse> resp = list.stream().map(this::toResponse).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponseUtil.success(resp));
    }

    @GetMapping("/{id}/documents")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getRequestDocuments(
            Authentication authentication,
            @PathVariable("id") UUID id) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new AppException(ErrorCode.INVALID_AUTHENTICATION);
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String email = userDetails.getUsername();

        NotaryRequest r = notaryRequestService.getById(id);

        if (!canAccessRequestForRead(userDetails, email, r)) {
            throw new AppException(ErrorCode.INVALID_AUTHORIZATION);
        }

        List<DocumentResponse> documents = notaryRequestService.getDocumentsByRequestId(id)
                .stream()
                .filter(document -> canExposeDocumentToUser(userDetails, r, document))
                .map(DocumentResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponseUtil.success(documents));
    }

    private boolean canExposeDocumentToUser(CustomUserDetails userDetails, NotaryRequest request, Document document) {
        boolean signedDocument = DocumentTypeService.SIGNED_DOCUMENT.equals(document.getDocType());
        if (!signedDocument) {
            return true;
        }

        String role = userDetails.getRole() != null ? userDetails.getRole().name() : "";
        boolean isClient = "CLIENT".equals(role);
        return !isClient || request.getStatus() == RequestStatus.COMPLETED;
    }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentResponse>> uploadDocument(
            Authentication authentication,
            @PathVariable("id") UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("docType") String docType
    ) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new AppException(ErrorCode.INVALID_AUTHENTICATION);
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String email = userDetails.getUsername();

        Document doc = notaryRequestService.uploadDocument(id, email, file, docType);
        URI location = URI.create("/api/documents/" + doc.getDocumentId());
        var resp = DocumentResponse.fromEntity(doc);
        // compute absolute path from project root + relative filePath (doc.filePath stores relative path)
        try {
            var projectRoot = notaryRequestService.getProjectRootPublic();
            if (resp.getFilePath() != null) {
                String abs = projectRoot.resolve(Path.of(resp.getFilePath())).toAbsolutePath().toString();
                resp.setAbsolutePath(abs);
            }
        } catch (Exception ignored) {
        }
        return ResponseEntity.created(location).body(
                ApiResponseUtil.created(resp, "Tải lên tài liệu thành công")
        );
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<NotaryRequestResponse>> cancelRequest(
            Authentication authentication,
            @PathVariable("id") UUID id) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new AppException(ErrorCode.INVALID_AUTHENTICATION);
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String email = userDetails.getUsername();

        NotaryRequest updated = notaryRequestService.cancelRequest(id, email);
        return ResponseEntity.ok(ApiResponseUtil.success(toResponse(updated), "Hủy yêu cầu thành công"));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<NotaryRequestResponse>> rejectRequest(
            Authentication authentication,
            @PathVariable("id") UUID id,
            @Valid @RequestBody RejectNotaryRequestRequest request) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new AppException(ErrorCode.INVALID_AUTHENTICATION);
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String role = userDetails.getRole() != null ? userDetails.getRole().name() : "";
        boolean isNotary = "NOTARY".equals(role);
        boolean isAdmin = "ADMIN".equals(role);
        if (!isNotary && !isAdmin) {
            throw new AppException(ErrorCode.INVALID_AUTHORIZATION);
        }

        // Notary must be VERIFIED to process requests
        assertNotaryVerified(userDetails);

        NotaryRequest updated = notaryRequestService.rejectRequest(id, userDetails.getUsername(), request.getReason());
        return ResponseEntity.ok(ApiResponseUtil.success(toResponse(updated), "Từ chối yêu cầu thành công"));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<NotaryRequestResponse>> acceptRequest(
            Authentication authentication,
            @PathVariable("id") UUID id,
            @Valid @RequestBody(required = false) AcceptNotaryRequestRequest request) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new AppException(ErrorCode.INVALID_AUTHENTICATION);
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String role = userDetails.getRole() != null ? userDetails.getRole().name() : "";
        boolean isNotary = "NOTARY".equals(role);
        if (!isNotary) {
            throw new AppException(ErrorCode.INVALID_AUTHORIZATION);
        }

        // Notary must be VERIFIED to accept requests
        assertNotaryVerified(userDetails);

        NotaryRequest updated = notaryRequestService.acceptRequest(
                id,
                userDetails.getUsername(),
                request != null ? request.getTemplateId() : null
        );
        return ResponseEntity.ok(ApiResponseUtil.success(toResponse(updated), "Tiếp nhận yêu cầu thành công"));
    }

    @PostMapping("/{id}/schedule")
    public ResponseEntity<ApiResponse<AppointmentResponse>> scheduleAppointment(
            Authentication authentication,
            @PathVariable("id") UUID id,
            @Valid @RequestBody ScheduleAppointmentRequest request) {

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new AppException(ErrorCode.INVALID_AUTHENTICATION);
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String role = userDetails.getRole() != null ? userDetails.getRole().name() : "";
        boolean isNotary = "NOTARY".equals(role);
        boolean isAdmin = "ADMIN".equals(role);
        if (!isNotary && !isAdmin) {
            throw new AppException(ErrorCode.INVALID_AUTHORIZATION);
        }

        // Notary must be VERIFIED to schedule appointments
        assertNotaryVerified(userDetails);

        AppointmentResponse response = notaryRequestService.scheduleAppointment(id, userDetails.getUsername(), request);
        URI location = URI.create("/api/appointments/" + response.getAppointmentId());
        return ResponseEntity.created(location).body(
                ApiResponseUtil.created(response, "Lên lịch cuộc hẹn thành công")
        );
    }

    @GetMapping("/filter")
    public ResponseEntity<ApiResponse<Page<NotaryRequestResponse>>> filterRequestsForNotary(
            Authentication authentication,
            @RequestParam(value = "status", required = false) RequestStatus status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new AppException(ErrorCode.INVALID_AUTHENTICATION);
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        String role = userDetails.getRole() != null ? userDetails.getRole().name() : "";
        boolean isNotary = "NOTARY".equals(role);
        boolean isAdmin = "ADMIN".equals(role);
        if (!isNotary && !isAdmin) {
            throw new AppException(ErrorCode.INVALID_AUTHORIZATION);
        }

        UUID notaryUserId = userDetails.getId();
        PageRequest pr = PageRequest.of(Math.max(0, page), Math.max(1, size));
        Page<NotaryRequest> pageResult = notaryRequestService.listForNotaryByStatus(notaryUserId, status, pr);
        Page<NotaryRequestResponse> resp = pageResult.map(this::toResponse);
        return ResponseEntity.ok(ApiResponseUtil.success(resp));
    }

    @GetMapping("/me/accepted")
    public ResponseEntity<ApiResponse<Page<NotaryRequestResponse>>> listAcceptedRequestsForCurrentNotary(
            Authentication authentication,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new AppException(ErrorCode.INVALID_AUTHENTICATION);
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String role = userDetails.getRole() != null ? userDetails.getRole().name() : "";
        if (!"NOTARY".equals(role)) {
            throw new AppException(ErrorCode.INVALID_AUTHORIZATION);
        }

        PageRequest pr = PageRequest.of(Math.max(0, page), Math.max(1, size));
        Page<NotaryRequest> pageResult = notaryRequestService.listAcceptedByNotary(userDetails.getId(), pr);
        Page<NotaryRequestResponse> resp = pageResult.map(this::toResponse);
        return ResponseEntity.ok(ApiResponseUtil.success(resp));
    }
}
