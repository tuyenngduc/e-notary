package com.actvn.enotary.service;

import com.actvn.enotary.dto.request.CreateVideoSessionRequest;
import com.actvn.enotary.dto.response.DocumentResponse;
import com.actvn.enotary.dto.response.SignVideoDocumentResponse;
import com.actvn.enotary.dto.response.VideoSessionResponse;
import com.actvn.enotary.entity.Appointment;
import com.actvn.enotary.entity.Document;
import com.actvn.enotary.entity.NotaryRequest;
import com.actvn.enotary.entity.NotaryServiceType;
import com.actvn.enotary.entity.Payment;
import com.actvn.enotary.entity.Signature;
import com.actvn.enotary.entity.User;
import com.actvn.enotary.entity.VideoSession;
import com.actvn.enotary.enums.AppointmentStatus;
import com.actvn.enotary.enums.PaymentStatus;
import com.actvn.enotary.enums.RequestStatus;
import com.actvn.enotary.enums.ServiceType;
import com.actvn.enotary.enums.VideoSessionStatus;
import com.actvn.enotary.exception.AppException;
import com.actvn.enotary.repository.AppointmentRepository;
import com.actvn.enotary.repository.DocumentRepository;
import com.actvn.enotary.repository.NotaryRequestRepository;
import com.actvn.enotary.repository.NotaryServiceTypeRepository;
import com.actvn.enotary.repository.PaymentRepository;
import com.actvn.enotary.repository.SignatureRepository;
import com.actvn.enotary.repository.VideoSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j

@Service
@RequiredArgsConstructor
public class VideoSessionService {

    private final VideoSessionRepository videoSessionRepository;
    private final AppointmentRepository appointmentRepository;
    private final NotaryRequestRepository notaryRequestRepository;
    private final DocumentRepository documentRepository;
    private final NotaryServiceTypeRepository notaryServiceTypeRepository;
    private final SignatureRepository signatureRepository;
    private final PaymentRepository paymentRepository;
    private final BlockchainService blockchainService;
    private final NotaryRequestService notaryRequestService;

    @Value("${app.meeting.base-url:http://localhost:8080}")
    private String baseUrl;

    @Transactional
    public VideoSessionResponse createVideoSession(CreateVideoSessionRequest request) {
        Appointment appointment = appointmentRepository.findById(request.getAppointmentId())
                .orElseThrow(() -> new AppException("Không tìm thấy lịch hẹn", HttpStatus.NOT_FOUND));

        if (appointment.getRequest() == null || appointment.getRequest().getServiceType() != ServiceType.ONLINE) {
            throw new AppException("Video session chỉ dành cho cuộc hẹn ONLINE", HttpStatus.BAD_REQUEST);
        }

        if (videoSessionRepository.existsByAppointmentAppointmentId(request.getAppointmentId())) {
            throw new AppException("Lịch hẹn này đã có video session", HttpStatus.CONFLICT);
        }

        VideoSession session = new VideoSession();
        session.setAppointment(appointment);

        String roomId = "room_" + UUID.randomUUID().toString().substring(0, 8);
        String sessionToken = UUID.randomUUID().toString();

        session.setRoomId(roomId);
        session.setSessionToken(sessionToken);

        String meetingUrl = baseUrl + "/api/video/room/" + roomId + "?token=" + sessionToken;
        session.setMeetingUrl(meetingUrl);

        session.setStatus(VideoSessionStatus.PENDING);
        session.setCreatedAt(OffsetDateTime.now());
        session.setUpdatedAt(OffsetDateTime.now());

        VideoSession saved = videoSessionRepository.save(session);

        appointment.setMeetingUrl(meetingUrl);
        appointmentRepository.save(appointment);

        return toResponse(saved);
    }

    public VideoSessionResponse getVideoSessionByAppointmentId(UUID appointmentId) {
        VideoSession session = videoSessionRepository.findByAppointmentAppointmentId(appointmentId)
                .orElseThrow(() -> new AppException("Không tìm thấy video session", HttpStatus.NOT_FOUND));

        return toResponse(session);
    }

    public VideoSessionResponse getVideoSession(UUID sessionId) {
        VideoSession session = videoSessionRepository.findById(sessionId)
                .orElseThrow(() -> new AppException("Không tìm thấy video session", HttpStatus.NOT_FOUND));

        return toResponse(session);
    }

    @Transactional
    public Document saveEvidencePhoto(UUID sessionId, String notaryEmail, String imageData) {
        VideoSession session = videoSessionRepository.findById(sessionId)
                .orElseThrow(() -> new AppException("Không tìm thấy video session", HttpStatus.NOT_FOUND));

        NotaryRequest request = session.getAppointment() != null ? session.getAppointment().getRequest() : null;
        if (request == null || request.getNotary() == null || request.getNotary().getEmail() == null
                || !request.getNotary().getEmail().equalsIgnoreCase(notaryEmail)) {
            throw new AppException("Chỉ công chứng viên phụ trách hồ sơ mới được lưu ảnh bằng chứng", HttpStatus.FORBIDDEN);
        }

        StoredEvidence stored = storeEvidenceImage(sessionId, imageData);

        Document document = new Document();
        document.setRequest(request);
        document.setDocType(DocumentTypeService.EVIDENCE_PHOTO);
        document.setFilePath(stored.relativePath());
        document.setFileHash(stored.hash());
        document.setCreatedAt(OffsetDateTime.now());
        return documentRepository.save(document);
    }

    @Transactional
    public SignVideoDocumentResponse signDocument(
            UUID sessionId,
            String userEmail,
            UUID sourceDocumentId,
            String signatureValue,
            Integer pageNumber,
            Double xPercent,
            Double yPercent,
            Double widthPercent,
            Double heightPercent
    ) {
        if (signatureValue == null || signatureValue.isBlank()) {
            throw new AppException("Chữ ký số không hợp lệ", HttpStatus.BAD_REQUEST);
        }
        validateSignaturePlacement(pageNumber, xPercent, yPercent, widthPercent, heightPercent);

        VideoSession session = videoSessionRepository.findById(sessionId)
                .orElseThrow(() -> new AppException("Không tìm thấy video session", HttpStatus.NOT_FOUND));
        if (session.getStatus() == VideoSessionStatus.FINISHED || session.getStatus() == VideoSessionStatus.CANCELLED) {
            throw new AppException("Phiên video đã kết thúc hoặc đã hủy", HttpStatus.BAD_REQUEST);
        }
        NotaryRequest request = session.getAppointment() != null ? session.getAppointment().getRequest() : null;
        if (request == null || request.getClient() == null || request.getNotary() == null) {
            throw new AppException("Dữ liệu phiên ký số không hợp lệ", HttpStatus.BAD_REQUEST);
        }

        User signer = resolveSigner(request, userEmail);
        boolean signerIsClient = request.getClient().getUserId().equals(signer.getUserId());
        boolean signerIsNotary = request.getNotary().getUserId().equals(signer.getUserId());

        Document sourceDocument = documentRepository.findById(sourceDocumentId)
                .orElseThrow(() -> new AppException("Không tìm thấy văn bản trình chiếu", HttpStatus.NOT_FOUND));
        if (sourceDocument.getRequest() == null
                || !request.getRequestId().equals(sourceDocument.getRequest().getRequestId())) {
            throw new AppException("Văn bản trình chiếu không thuộc hồ sơ này", HttpStatus.BAD_REQUEST);
        }
        if (!NotaryRequestService.DRAFT_CONTRACT_DOC_TYPE.equals(sourceDocument.getDocType())) {
            throw new AppException("Chỉ có thể ký văn bản mẫu đang trình chiếu", HttpStatus.BAD_REQUEST);
        }
        if (!isPdfDocument(sourceDocument)) {
            throw new AppException("Chỉ hỗ trợ ký trực tiếp lên văn bản PDF", HttpStatus.BAD_REQUEST);
        }

        Document signedDocument = getOrCreateSignedDocument(request, sourceDocument);
        boolean clientSignedBefore = signatureRepository.existsByDocument_DocumentIdAndUser_UserId(
                signedDocument.getDocumentId(),
                request.getClient().getUserId()
        );

        if (signerIsNotary && !clientSignedBefore) {
            throw new AppException("Công chứng viên chỉ ký sau khi người dân đã ký số", HttpStatus.BAD_REQUEST);
        }

        signatureRepository.findByDocument_DocumentIdAndUser_UserId(signedDocument.getDocumentId(), signer.getUserId())
                .orElseGet(() -> {
                    stampSignatureOnPdf(signedDocument, signatureValue, pageNumber, xPercent, yPercent, widthPercent, heightPercent);
                    Signature signature = new Signature();
                    signature.setDocument(signedDocument);
                    signature.setUser(signer);
                    signature.setSignatureValue(signatureValue);
                    signature.setSignedAt(OffsetDateTime.now());
                    signature.setIsValid(true);
                    return signatureRepository.save(signature);
                });

        boolean clientSigned = signatureRepository.existsByDocument_DocumentIdAndUser_UserId(
                signedDocument.getDocumentId(),
                request.getClient().getUserId()
        );
        boolean notarySigned = signatureRepository.existsByDocument_DocumentIdAndUser_UserId(
                signedDocument.getDocumentId(),
                request.getNotary().getUserId()
        );
        boolean completed = clientSigned && notarySigned;

        if (completed) {
            blockchainService.anchorSignedDocument(request, signedDocument);
            completeSignedRequest(request);
        }

        return SignVideoDocumentResponse.builder()
                .signedDocument(DocumentResponse.fromEntity(signedDocument))
                .clientSigned(clientSigned)
                .notarySigned(notarySigned)
                .completed(completed)
                .requestStatus(request.getStatus())
                .build();
    }

    private void validateSignaturePlacement(Integer pageNumber, Double xPercent, Double yPercent, Double widthPercent, Double heightPercent) {
        if (pageNumber == null || pageNumber < 1
                || xPercent == null || yPercent == null || widthPercent == null || heightPercent == null
                || xPercent < 0 || xPercent > 100
                || yPercent < 0 || yPercent > 100
                || widthPercent <= 0 || widthPercent > 100
                || heightPercent <= 0 || heightPercent > 100
                || xPercent + widthPercent > 100
                || yPercent + heightPercent > 100) {
            throw new AppException("Vị trí chữ ký trên PDF không hợp lệ", HttpStatus.BAD_REQUEST);
        }
    }

    private boolean isPdfDocument(Document document) {
        String contentType = document.getContentType();
        String filePath = document.getFilePath();
        String originalName = document.getOriginalFileName();
        return "application/pdf".equalsIgnoreCase(contentType)
                || (filePath != null && filePath.toLowerCase().endsWith(".pdf"))
                || (originalName != null && originalName.toLowerCase().endsWith(".pdf"));
    }

    private void stampSignatureOnPdf(
            Document signedDocument,
            String signatureValue,
            int pageNumber,
            double xPercent,
            double yPercent,
            double widthPercent,
            double heightPercent
    ) {
        try {
            Path pdfPath = notaryRequestService.resolveStoredFilePath(signedDocument.getFilePath());
            if (!Files.exists(pdfPath)) {
                throw new AppException("File văn bản đã ký không tồn tại", HttpStatus.NOT_FOUND);
            }

            byte[] signatureBytes = decodeSignatureImage(signatureValue);
            Path tempPath = pdfPath.resolveSibling(pdfPath.getFileName() + ".signing.tmp");
            try (PDDocument pdf = PDDocument.load(pdfPath.toFile())) {
                if (pageNumber < 1 || pageNumber > pdf.getNumberOfPages()) {
                    throw new AppException("Trang ký không tồn tại trong PDF", HttpStatus.BAD_REQUEST);
                }

                PDPage page = pdf.getPage(pageNumber - 1);
                PDRectangle pageBox = page.getCropBox();
                float pageWidth = pageBox.getWidth();
                float pageHeight = pageBox.getHeight();
                float signatureWidth = (float) (pageWidth * widthPercent / 100d);
                float signatureHeight = (float) (pageHeight * heightPercent / 100d);
                float x = pageBox.getLowerLeftX() + (float) (pageWidth * xPercent / 100d);
                float yFromTop = (float) (pageHeight * yPercent / 100d);
                float y = pageBox.getLowerLeftY() + pageHeight - yFromTop - signatureHeight;

                PDImageXObject signatureImage = PDImageXObject.createFromByteArray(pdf, signatureBytes, "signature.png");
                try (PDPageContentStream contentStream = new PDPageContentStream(
                        pdf,
                        page,
                        PDPageContentStream.AppendMode.APPEND,
                        true,
                        true
                )) {
                    contentStream.drawImage(signatureImage, x, y, signatureWidth, signatureHeight);
                }

                pdf.save(tempPath.toFile());
            }
            Files.move(tempPath, pdfPath, StandardCopyOption.REPLACE_EXISTING);

            byte[] updatedBytes = Files.readAllBytes(pdfPath);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            signedDocument.setFileHash(HexFormat.of().formatHex(digest.digest(updatedBytes)));
            signedDocument.setFileSize((long) updatedBytes.length);
            signedDocument.setContentType("application/pdf");
            signedDocument.setUpdatedAt(OffsetDateTime.now());
            documentRepository.save(signedDocument);
        } catch (AppException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new AppException("Không thể ghi chữ ký lên PDF", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception ex) {
            throw new AppException("Không thể xử lý chữ ký trên PDF", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private byte[] decodeSignatureImage(String signatureValue) {
        String base64 = signatureValue;
        int commaIndex = signatureValue.indexOf(',');
        if (signatureValue.startsWith("data:image/") && commaIndex >= 0) {
            base64 = signatureValue.substring(commaIndex + 1);
        }

        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            if (bytes.length == 0 || bytes.length > 2 * 1024 * 1024) {
                throw new AppException("Dữ liệu chữ ký không hợp lệ", HttpStatus.BAD_REQUEST);
            }
            return bytes;
        } catch (IllegalArgumentException ex) {
            throw new AppException("Dữ liệu chữ ký không hợp lệ", HttpStatus.BAD_REQUEST);
        }
    }

    public VideoSessionResponse verifySessionToken(String token) {
        VideoSession session = videoSessionRepository.findBySessionToken(token)
                .orElseThrow(() -> new AppException("Token không hợp lệ", HttpStatus.UNAUTHORIZED));

        return toResponse(session);
    }

    @Transactional
    public VideoSessionResponse joinSession(String roomId, String userEmail, String sessionToken) {
        log.info("[VideoSession] User {} attempting to join room {}", userEmail, roomId);
        VideoSession session = validateParticipantAccess(roomId, sessionToken, userEmail);
        Appointment appointment = session.getAppointment();
        boolean isNotary = appointment.getRequest().getNotary() != null
                && appointment.getRequest().getNotary().getEmail() != null
                && appointment.getRequest().getNotary().getEmail().equalsIgnoreCase(userEmail);
        boolean isClient = appointment.getRequest().getClient() != null
                && appointment.getRequest().getClient().getEmail() != null
                && appointment.getRequest().getClient().getEmail().equalsIgnoreCase(userEmail);

        log.debug("[VideoSession] Join confirmed: isNotary={}, isClient={}", isNotary, isClient);

        OffsetDateTime now = OffsetDateTime.now();
        if (isNotary && session.getNotaryJoinedAt() == null) {
            session.setNotaryJoinedAt(now);
            log.info("[VideoSession] Notary joined at {}", now);
        } else if (isClient && session.getClientJoinedAt() == null) {
            session.setClientJoinedAt(now);
            log.info("[VideoSession] Client joined at {}", now);
        }

        if (session.getNotaryJoinedAt() != null && session.getClientJoinedAt() != null) {
            session.setStatus(VideoSessionStatus.IN_PROGRESS);
            log.info("[VideoSession] Both participants joined, session IN_PROGRESS");
            transitionRequestToInVideoCall(session);
        } else if (session.getStatus() == VideoSessionStatus.PENDING && isNotary) {
            session.setStatus(VideoSessionStatus.NOTARY_JOINED);
            log.info("[VideoSession] First participant (Notary) joined");
        }

        session.setUpdatedAt(now);
        VideoSession updated = videoSessionRepository.save(session);
        log.info("[VideoSession] Session {} updated with status {}", session.getSessionId(), session.getStatus());

        return toResponse(updated);
    }

    public VideoSession validateParticipantAccess(String roomId, String sessionToken, String userEmail) {
        if (sessionToken == null || sessionToken.isBlank()) {
            log.warn("[VideoSession] Missing session token for room {}", roomId);
            throw new AppException("Thiếu token truy cập phòng họp", HttpStatus.UNAUTHORIZED);
        }

        VideoSession session = videoSessionRepository.findByRoomId(roomId)
                .orElseThrow(() -> {
                    log.warn("[VideoSession] Room {} not found", roomId);
                    return new AppException("Phòng họp không tồn tại", HttpStatus.NOT_FOUND);
                });

        if (!sessionToken.equals(session.getSessionToken())) {
            log.warn("[VideoSession] Invalid token for room {}", roomId);
            throw new AppException("Token phòng họp không hợp lệ", HttpStatus.UNAUTHORIZED);
        }

        Appointment appointment = session.getAppointment();
        if (appointment == null || appointment.getRequest() == null) {
            log.error("[VideoSession] Invalid video session data for room {}", roomId);
            throw new AppException("Dữ liệu phiên video không hợp lệ", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        boolean isNotary = appointment.getRequest().getNotary() != null
                && appointment.getRequest().getNotary().getEmail() != null
                && appointment.getRequest().getNotary().getEmail().equalsIgnoreCase(userEmail);
        boolean isClient = appointment.getRequest().getClient() != null
                && appointment.getRequest().getClient().getEmail() != null
                && appointment.getRequest().getClient().getEmail().equalsIgnoreCase(userEmail);

        if (!isNotary && !isClient) {
            log.warn("[VideoSession] Unauthorized access attempt to room {} by {}", roomId, userEmail);
            throw new AppException("Bạn không có quyền truy cập phòng họp này", HttpStatus.FORBIDDEN);
        }

        if (session.getStatus() == VideoSessionStatus.FINISHED || session.getStatus() == VideoSessionStatus.CANCELLED) {
            log.warn("[VideoSession] Room {} already finished/cancelled", roomId);
            throw new AppException("Phiên video đã kết thúc hoặc đã hủy", HttpStatus.BAD_REQUEST);
        }

        log.debug("[VideoSession] Access validation passed for {} in room {}", userEmail, roomId);
        return session;
    }

    @Transactional
    public VideoSessionResponse endSession(UUID sessionId, String reason) {
        log.info("[VideoSession] Ending session {} with reason: {}", sessionId, reason);
        VideoSession session = videoSessionRepository.findById(sessionId)
                .orElseThrow(() -> {
                    log.error("[VideoSession] Session {} not found", sessionId);
                    return new AppException("Không tìm thấy video session", HttpStatus.NOT_FOUND);
                });

        OffsetDateTime now = OffsetDateTime.now();
        session.setEndedAt(now);

        if (session.getClientJoinedAt() != null) {
            long seconds = java.time.temporal.ChronoUnit.SECONDS.between(
                    session.getClientJoinedAt(),
                    now
            );
            session.setDurationSeconds(seconds);
            log.info("[VideoSession] Session {} duration: {} seconds", sessionId, seconds);
        } else if (session.getNotaryJoinedAt() != null) {
            long seconds = java.time.temporal.ChronoUnit.SECONDS.between(
                    session.getNotaryJoinedAt(),
                    now
            );
            session.setDurationSeconds(seconds);
        }

        session.setStatus(VideoSessionStatus.FINISHED);
        session.setNotes(reason);
        session.setUpdatedAt(now);

        VideoSession updated = videoSessionRepository.save(session);
        log.info("[VideoSession] Session {} marked as FINISHED", sessionId);

        transitionRequestAfterVideoCallEnded(session);

        return toResponse(updated);
    }

    @Transactional
    public VideoSessionResponse cancelSession(UUID sessionId, String reason) {
        log.info("[VideoSession] Cancelling session {} with reason: {}", sessionId, reason);
        VideoSession session = videoSessionRepository.findById(sessionId)
                .orElseThrow(() -> new AppException("Không tìm thấy video session", HttpStatus.NOT_FOUND));

        session.setStatus(VideoSessionStatus.CANCELLED);
        session.setNotes(reason);
        session.setUpdatedAt(OffsetDateTime.now());

        VideoSession updated = videoSessionRepository.save(session);
        log.info("[VideoSession] Session {} marked as CANCELLED", sessionId);
        transitionRequestAfterVideoCallCancelled(session);
        return toResponse(updated);
    }

    private void transitionRequestToInVideoCall(VideoSession session) {
        try {
            NotaryRequest request = session.getAppointment().getRequest();
            if (request != null && request.getStatus() == RequestStatus.SCHEDULED) {
                request.setStatus(RequestStatus.IN_VIDEO_CALL);
                request.setUpdatedAt(OffsetDateTime.now());
                notaryRequestRepository.save(request);
                log.info("[VideoSession] NotaryRequest {} transitioned to IN_VIDEO_CALL", request.getRequestId());
            }
        } catch (Exception ex) {
            log.warn("[VideoSession] Could not transition request to IN_VIDEO_CALL: {}", ex.getMessage());
        }
    }

    private void transitionRequestAfterVideoCallEnded(VideoSession session) {
        try {
            Appointment appointment = session.getAppointment();
            if (appointment != null && appointment.getStatus() == AppointmentStatus.PENDING) {
                appointment.setStatus(AppointmentStatus.FINISHED);
                appointmentRepository.save(appointment);
                log.info("[VideoSession] Appointment {} transitioned to FINISHED after video call ended", appointment.getAppointmentId());
            }

            NotaryRequest request = appointment != null ? appointment.getRequest() : null;
            if (request != null
                    && (request.getStatus() == RequestStatus.IN_VIDEO_CALL || request.getStatus() == RequestStatus.SCHEDULED)) {
                request.setStatus(RequestStatus.AWAITING_PAYMENT);
                request.setUpdatedAt(OffsetDateTime.now());
                notaryRequestRepository.save(request);
                ensurePaymentInvoice(request);
                log.info("[VideoSession] NotaryRequest {} transitioned to AWAITING_PAYMENT after video call ended", request.getRequestId());
            }
        } catch (Exception ex) {
            log.warn("[VideoSession] Could not transition request after video call ended: {}", ex.getMessage());
        }
    }

    private User resolveSigner(NotaryRequest request, String userEmail) {
        if (request.getClient().getEmail() != null && request.getClient().getEmail().equalsIgnoreCase(userEmail)) {
            return request.getClient();
        }
        if (request.getNotary().getEmail() != null && request.getNotary().getEmail().equalsIgnoreCase(userEmail)) {
            return request.getNotary();
        }
        throw new AppException("Bạn không có quyền ký văn bản của phiên này", HttpStatus.FORBIDDEN);
    }

    private Document getOrCreateSignedDocument(NotaryRequest request, Document sourceDocument) {
        return documentRepository.findByRequestIdAndDocTypeOrderByCreatedAtDesc(
                        request.getRequestId(),
                        DocumentTypeService.SIGNED_DOCUMENT
                )
                .stream()
                .findFirst()
                .orElseGet(() -> createSignedDocumentCopy(request, sourceDocument));
    }

    private Document createSignedDocumentCopy(NotaryRequest request, Document sourceDocument) {
        try {
            Path projectRoot = findProjectRoot();
            Path source = notaryRequestService.resolveStoredFilePath(sourceDocument.getFilePath());
            if (!Files.exists(source)) {
                throw new AppException("File văn bản trình chiếu không tồn tại", HttpStatus.NOT_FOUND);
            }

            Path uploadsDir = projectRoot.resolve("uploads")
                    .resolve("requests")
                    .resolve(request.getRequestId().toString())
                    .normalize();
            Files.createDirectories(uploadsDir);

            String originalName = sourceDocument.getOriginalFileName() == null || sourceDocument.getOriginalFileName().isBlank()
                    ? "signed-document.bin"
                    : sourceDocument.getOriginalFileName();
            String signedName = originalName.startsWith("signed-") ? originalName : "signed-" + originalName;
            Path target = uploadsDir.resolve(UUID.randomUUID() + "-" + signedName).normalize();
            if (!target.startsWith(uploadsDir)) {
                throw new AppException("Tên file ký số không hợp lệ", HttpStatus.BAD_REQUEST);
            }

            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            byte[] bytes = Files.readAllBytes(target);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String hash = HexFormat.of().formatHex(digest.digest(bytes));

            Document signedDocument = new Document();
            signedDocument.setRequest(request);
            signedDocument.setDocType(DocumentTypeService.SIGNED_DOCUMENT);
            signedDocument.setFilePath(projectRoot.relativize(target).toString().replace("\\", "/"));
            signedDocument.setOriginalFileName(signedName);
            signedDocument.setContentType(sourceDocument.getContentType());
            signedDocument.setFileSize((long) bytes.length);
            signedDocument.setFileHash(hash);
            signedDocument.setCreatedAt(OffsetDateTime.now());
            return documentRepository.save(signedDocument);
        } catch (AppException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new AppException("Lỗi khi tạo văn bản đã ký", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception ex) {
            throw new AppException("Lỗi khi xử lý văn bản đã ký", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void completeSignedRequest(NotaryRequest request) {
        if (request.getStatus() != RequestStatus.COMPLETED
                && request.getStatus() != RequestStatus.CANCELLED
                && request.getStatus() != RequestStatus.REJECTED) {
            request.setStatus(RequestStatus.AWAITING_PAYMENT);
            request.setUpdatedAt(OffsetDateTime.now());
            notaryRequestRepository.save(request);
        }

        ensurePaymentInvoice(request);
    }

    private Payment ensurePaymentInvoice(NotaryRequest request) {
        return paymentRepository.findByRequest_RequestId(request.getRequestId())
                .orElseGet(() -> {
                    Payment payment = new Payment();
                    payment.setRequest(request);
                    payment.setAmount(resolvePaymentAmount(request));
                    payment.setPaymentStatus(PaymentStatus.PENDING);
                    payment.setCreatedAt(OffsetDateTime.now());
                    return paymentRepository.save(payment);
                });
    }

    private BigDecimal resolvePaymentAmount(NotaryRequest request) {
        if (request.getContractType() == null) {
            return BigDecimal.ZERO;
        }

        return notaryServiceTypeRepository.findByServiceCode(request.getContractType().name())
                .map(NotaryServiceType::getBasePrice)
                .orElse(BigDecimal.ZERO);
    }

    private void transitionRequestAfterVideoCallCancelled(VideoSession session) {
        try {
            Appointment appointment = session.getAppointment();
            if (appointment != null && appointment.getStatus() == AppointmentStatus.PENDING) {
                appointment.setStatus(AppointmentStatus.CANCELLED);
                appointmentRepository.save(appointment);
                log.info("[VideoSession] Appointment {} transitioned to CANCELLED after video call cancelled", appointment.getAppointmentId());
            }

            NotaryRequest request = appointment != null ? appointment.getRequest() : null;
            if (request != null && request.getStatus() == RequestStatus.IN_VIDEO_CALL) {
                request.setStatus(RequestStatus.SCHEDULED);
                request.setUpdatedAt(OffsetDateTime.now());
                notaryRequestRepository.save(request);
                log.info("[VideoSession] NotaryRequest {} transitioned back to SCHEDULED after video call cancelled", request.getRequestId());
            }
        } catch (Exception ex) {
            log.warn("[VideoSession] Could not transition request after video call cancelled: {}", ex.getMessage());
        }
    }

    private StoredEvidence storeEvidenceImage(UUID sessionId, String imageData) {
        if (imageData == null || imageData.isBlank()) {
            throw new AppException("Ảnh bằng chứng không hợp lệ", HttpStatus.BAD_REQUEST);
        }

        String base64 = imageData;
        int commaIndex = imageData.indexOf(',');
        if (imageData.startsWith("data:image/") && commaIndex >= 0) {
            base64 = imageData.substring(commaIndex + 1);
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new AppException("Dữ liệu ảnh bằng chứng không hợp lệ", HttpStatus.BAD_REQUEST);
        }

        if (bytes.length == 0 || bytes.length > 5 * 1024 * 1024) {
            throw new AppException("Kích thước ảnh bằng chứng không hợp lệ", HttpStatus.BAD_REQUEST);
        }

        try {
            Path projectRoot = findProjectRoot();
            Path uploadsDir = projectRoot.resolve("uploads").resolve("video").resolve("evidence").resolve(sessionId.toString()).normalize();
            Files.createDirectories(uploadsDir);

            Path target = uploadsDir.resolve(UUID.randomUUID() + "-evidence.jpg").normalize();
            if (!target.startsWith(uploadsDir)) {
                throw new AppException("Tên file không hợp lệ", HttpStatus.BAD_REQUEST);
            }

            Files.write(target, bytes);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String hash = HexFormat.of().formatHex(digest.digest(bytes));
            Path relative = projectRoot.relativize(target);
            return new StoredEvidence(relative.toString().replace("\\", "/"), hash);
        } catch (IOException ex) {
            throw new AppException("Lỗi khi lưu ảnh bằng chứng", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException("Lỗi khi xử lý ảnh bằng chứng", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Path findProjectRoot() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path cur = cwd;
        while (cur != null) {
            if (Files.exists(cur.resolve("pom.xml"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        return cwd;
    }

    private VideoSessionResponse toResponse(VideoSession session) {
        NotaryRequest request = session.getAppointment() != null ? session.getAppointment().getRequest() : null;
        DocumentResponse draftDocument = request != null
                ? documentRepository.findByRequestIdAndDocTypeOrderByCreatedAtDesc(
                        request.getRequestId(),
                        NotaryRequestService.DRAFT_CONTRACT_DOC_TYPE
                ).stream().findFirst().map(DocumentResponse::fromEntity).orElse(null)
                : null;
        return VideoSessionResponse.fromEntity(session, requiresTemplate(request), draftDocument);
    }

    private boolean requiresTemplate(NotaryRequest request) {
        if (request == null || request.getContractType() == null) {
            return true;
        }

        return notaryServiceTypeRepository.findByServiceCode(request.getContractType().name())
                .map(serviceType -> serviceType.getRequiresTemplate() == null || Boolean.TRUE.equals(serviceType.getRequiresTemplate()))
                .orElse(true);
    }

    private record StoredEvidence(String relativePath, String hash) {
    }
}

