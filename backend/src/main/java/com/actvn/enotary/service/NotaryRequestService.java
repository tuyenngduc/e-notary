package com.actvn.enotary.service;

import com.actvn.enotary.dto.request.NotaryRequestCreateRequest;
import com.actvn.enotary.dto.request.ScheduleAppointmentRequest;
import com.actvn.enotary.dto.response.AppointmentResponse;
import com.actvn.enotary.dto.response.DocumentRequirementResponse;
import com.actvn.enotary.entity.Appointment;
import com.actvn.enotary.entity.ContractTemplate;
import com.actvn.enotary.entity.DocumentType;
import com.actvn.enotary.entity.Document;
import com.actvn.enotary.entity.NotaryRequest;
import com.actvn.enotary.entity.User;
import com.actvn.enotary.entity.VideoSession;
import com.actvn.enotary.enums.AppointmentStatus;
import com.actvn.enotary.enums.RequestStatus;
import com.actvn.enotary.enums.ServiceType;
import com.actvn.enotary.enums.VideoSessionStatus;
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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotaryRequestService {
    public static final String DRAFT_CONTRACT_DOC_TYPE = "DRAFT_CONTRACT";
    private static final long MAX_DOCUMENT_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_DOCUMENT_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png", "doc", "docx");
    private static final Set<String> ALLOWED_DOCUMENT_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/octet-stream"
    );
    private static final Set<String> ALLOWED_VIDEO_EXTENSIONS = Set.of("mp4", "webm", "mov");
    private static final Set<String> ALLOWED_VIDEO_CONTENT_TYPES = Set.of(
            "video/mp4",
            "video/webm",
            "video/quicktime",
            "application/octet-stream"
    );

    private final NotaryRequestRepository notaryRequestRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final AppointmentRepository appointmentRepository;
    private final VideoSessionRepository videoSessionRepository;
    private final ContractTemplateRepository contractTemplateRepository;
    private final NotaryServiceTypeRepository notaryServiceTypeRepository;
    private final NotaryServiceDocumentRequirementRepository documentRequirementRepository;
    private final DocumentTypeService documentTypeService;
    private final NotificationService notificationService;

    @Value("${app.meeting.base-url:http://localhost:8080}")
    private String baseUrl;

    private boolean isClaimedByAnotherNotary(NotaryRequest request, User reviewer) {
        boolean isNotary = reviewer.getRole() != null && reviewer.getRole().name().equals("NOTARY");
        return isNotary
                && request.getNotary() != null
                && !request.getNotary().getUserId().equals(reviewer.getUserId());
    }

    @Transactional
    public NotaryRequest createRequest(String clientEmail, NotaryRequestCreateRequest req) {
        User client = userRepository.findByEmail(clientEmail)
                .orElseThrow(() -> new AppException("Không tìm thấy người dùng", HttpStatus.NOT_FOUND));

        NotaryRequest r = new NotaryRequest();
        validateActiveServiceType(req);
        r.setClient(client);
        r.setNotary(null);
        r.setServiceType(req.getServiceType());
        r.setContractType(req.getContractType());
        r.setDescription(req.getDescription());
        r.setStatus(RequestStatus.NEW);
        r.setCreatedAt(OffsetDateTime.now());
        r.setUpdatedAt(OffsetDateTime.now());

        return notaryRequestRepository.save(r);
    }

    public NotaryRequest getById(UUID requestId) {
        return notaryRequestRepository.findById(requestId)
                .orElseThrow(() -> new AppException("Không tìm thấy yêu cầu công chứng", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public NotaryRequest acceptRequest(UUID requestId, String notaryEmail) {
        return acceptRequest(requestId, notaryEmail, null);
    }

    @Transactional
    public NotaryRequest acceptRequest(UUID requestId, String notaryEmail, UUID templateId) {
        User notary = userRepository.findByEmail(notaryEmail)
                .orElseThrow(() -> new AppException("Không tìm thấy người dùng", HttpStatus.NOT_FOUND));

        boolean isNotary = notary.getRole() != null && notary.getRole().name().equals("NOTARY");
        if (!isNotary) {
            throw new AppException("Chỉ công chứng viên mới có quyền tiếp nhận yêu cầu", HttpStatus.FORBIDDEN);
        }

        NotaryRequest request = notaryRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new AppException("Không tìm thấy yêu cầu công chứng", HttpStatus.NOT_FOUND));

        if (isClaimedByAnotherNotary(request, notary)) {
            throw alreadyAssignedException();
        }

        if (requiresTemplate(request) && getLatestDraftContract(request.getRequestId()) == null) {
            throw new AppException("Dịch vụ này cần tải lên file mẫu văn bản trước khi tiếp nhận", HttpStatus.BAD_REQUEST);
        }

        if (request.getStatus() == RequestStatus.ACCEPTED
                && request.getNotary() != null
                && request.getNotary().getUserId().equals(notary.getUserId())) {
            return request;
        }

        if (request.getStatus() != RequestStatus.NEW && request.getStatus() != RequestStatus.PROCESSING) {
            throw new AppException("Yêu cầu không ở trạng thái chờ tiếp nhận", HttpStatus.BAD_REQUEST);
        }

        DocumentRequirementResponse documentRequirements = buildDocumentRequirements(request);
        if (!documentRequirements.isReadyForAccept()) {
            throw new AppException(
                    ErrorCode.REQUEST_MISSING_REQUIRED_DOCUMENTS,
                    Map.of("missingDocTypes", documentRequirements.getMissingDocTypes())
            );
        }

        request.setNotary(notary);
        request.setSelectedTemplate(null);
        request.setStatus(RequestStatus.ACCEPTED);
        request.setUpdatedAt(OffsetDateTime.now());
        return notaryRequestRepository.save(request);
    }

    private ContractTemplate resolveSelectedTemplate(NotaryRequest request, UUID templateId) {
        if (templateId == null) {
            return null;
        }

        ContractTemplate template = contractTemplateRepository.findById(templateId)
                .orElseThrow(() -> new AppException("KhÃ´ng tÃ¬m tháº¥y máº«u há»£p Ä‘á»“ng", HttpStatus.NOT_FOUND));

        if (!Boolean.TRUE.equals(template.getIsActive())) {
            throw new AppException("Máº«u há»£p Ä‘á»“ng Ä‘Ã£ ngÆ°ng Ã¡p dá»¥ng", HttpStatus.BAD_REQUEST);
        }

        String templateServiceCode = template.getServiceType() != null ? template.getServiceType().getServiceCode() : null;
        if (request.getContractType() != null
                && templateServiceCode != null
                && !request.getContractType().name().equals(templateServiceCode)) {
            throw new AppException("Máº«u há»£p Ä‘á»“ng khÃ´ng khá»›p loáº¡i há»£p Ä‘á»“ng cá»§a yÃªu cáº§u", HttpStatus.BAD_REQUEST);
        }

        return template;
    }

    public boolean requiresTemplate(NotaryRequest request) {
        if (request == null || request.getContractType() == null) {
            return true;
        }

        return notaryServiceTypeRepository.findByServiceCode(request.getContractType().name())
                .map(serviceType -> serviceType.getRequiresTemplate() == null || Boolean.TRUE.equals(serviceType.getRequiresTemplate()))
                .orElse(true);
    }

    private Set<String> requiredDocTypesForAccept(NotaryRequest request) {
        if (request.getContractType() != null) {
            List<String> configuredDocTypes = documentRequirementRepository.findDocTypesByServiceCode(request.getContractType().name());
            if (!configuredDocTypes.isEmpty()) {
                return withoutRequestForm(configuredDocTypes);
            }
        }

        return legacyRequiredDocTypesForAccept(request);
    }

    private Set<String> legacyRequiredDocTypesForAccept(NotaryRequest request) {
        Set<String> required = new LinkedHashSet<>(List.of("ID_CARD", "DRAFT_CONTRACT"));
        if (request.getContractType() == com.actvn.enotary.enums.ContractType.TRANSFER_OF_PROPERTY) {
            required.add("PROPERTY_PAPER");
        }
        return required;
    }

    private Set<String> withoutRequestForm(Iterable<String> docTypes) {
        Set<String> filtered = new LinkedHashSet<>();
        for (String docType : docTypes) {
            if (!DocumentTypeService.REQUEST_FORM.equals(docType)) {
                filtered.add(docType);
            }
        }
        return filtered;
    }

    private void validateActiveServiceType(NotaryRequestCreateRequest req) {
        if (req.getContractType() == null) {
            return;
        }

        notaryServiceTypeRepository.findByServiceCode(req.getContractType().name())
                .filter(serviceType -> Boolean.TRUE.equals(serviceType.getIsActive()))
                .orElseThrow(() -> new AppException("Loại hợp đồng chưa được cấu hình hoặc đã ngừng áp dụng", HttpStatus.BAD_REQUEST));
    }

    public DocumentRequirementResponse getDocumentRequirements(UUID requestId) {
        return buildDocumentRequirements(getById(requestId));
    }

    public Document getLatestDraftContract(UUID requestId) {
        return documentRepository.findByRequestIdAndDocTypeOrderByCreatedAtDesc(requestId, DRAFT_CONTRACT_DOC_TYPE)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private DocumentRequirementResponse buildDocumentRequirements(NotaryRequest request) {
        List<String> uploadedDocTypes = sortDocTypes(documentRepository.findDocTypesByRequestId(request.getRequestId()));
        Set<String> uploadedSet = new LinkedHashSet<>(uploadedDocTypes);

        List<String> requiredDocTypes = sortDocTypes(requiredDocTypesForAccept(request));
        List<String> missingDocTypes = requiredDocTypes.stream()
                .filter(requiredType -> !uploadedSet.contains(requiredType))
                .toList();

        return DocumentRequirementResponse.builder()
                .requiredDocTypes(requiredDocTypes)
                .uploadedDocTypes(uploadedDocTypes)
                .missingDocTypes(missingDocTypes)
                .requiredDocuments(requiredDocTypes.stream()
                        .map(docType -> toRequiredDocumentResponse(docType, uploadedSet.contains(docType), missingDocTypes.contains(docType)))
                        .toList())
                .readyForAccept(missingDocTypes.isEmpty())
                .build();
    }

    private DocumentRequirementResponse buildClientDocumentRequirementsForProcessing(NotaryRequest request) {
        List<String> uploadedDocTypes = sortDocTypes(documentRepository.findDocTypesByRequestId(request.getRequestId()));
        Set<String> uploadedSet = new LinkedHashSet<>(uploadedDocTypes);

        List<String> requiredDocTypes = sortDocTypes(requiredDocTypesForAccept(request));
        List<String> missingDocTypes = requiredDocTypes.stream()
                .filter(requiredType -> !requiresTemplate(request) || !DRAFT_CONTRACT_DOC_TYPE.equals(requiredType))
                .filter(requiredType -> !uploadedSet.contains(requiredType))
                .toList();

        return DocumentRequirementResponse.builder()
                .requiredDocTypes(requiredDocTypes)
                .uploadedDocTypes(uploadedDocTypes)
                .missingDocTypes(missingDocTypes)
                .readyForAccept(missingDocTypes.isEmpty())
                .build();
    }

    private com.actvn.enotary.dto.response.RequiredDocumentResponse toRequiredDocumentResponse(String docType, boolean uploaded, boolean missing) {
        try {
            DocumentType documentType = documentTypeService.getByCode(docType);
            return com.actvn.enotary.dto.response.RequiredDocumentResponse.builder()
                    .code(documentType.getCode())
                    .name(documentType.getName())
                    .source(documentType.getSource())
                    .allowedFileGroup(documentType.getAllowedFileGroup())
                    .uploaded(uploaded)
                    .missing(missing)
                    .build();
        } catch (AppException ex) {
            return com.actvn.enotary.dto.response.RequiredDocumentResponse.builder()
                    .code(docType)
                    .name(docType)
                    .source(DocumentTypeService.SOURCE_USER_UPLOAD)
                    .allowedFileGroup(DocumentTypeService.FILE_GROUP_DOCUMENT)
                    .uploaded(uploaded)
                    .missing(missing)
                    .build();
        }
    }

    private List<String> sortDocTypes(Iterable<String> docTypes) {
        Set<String> unique = new LinkedHashSet<>();
        for (String docType : docTypes) {
            if (docType != null && !docType.isBlank()) {
                unique.add(docType);
            }
        }
        return new java.util.ArrayList<>(unique);
    }

    public List<NotaryRequest> listForClient(UUID userId) {
        return notaryRequestRepository.findByClientUserId(userId);
    }

    public String getMeetingUrlByRequestId(UUID requestId) {
        return appointmentRepository.findByRequestRequestId(requestId)
                .map(Appointment::getMeetingUrl)
                .orElse(null);
    }

    public Optional<AppointmentResponse> getAppointmentResponseByRequestId(UUID requestId) {
        return appointmentRepository.findByRequestRequestId(requestId)
                .map(AppointmentResponse::fromEntity);
    }

    public Page<NotaryRequest> listForNotaryByStatus(UUID notaryUserId, RequestStatus status, Pageable pageable) {
        if (status == null) {
            return notaryRequestRepository.findByStatusAndNotaryIsNull(RequestStatus.PROCESSING, pageable);
        }
        if (status == RequestStatus.PROCESSING) {
            return notaryRequestRepository.findByStatusAndNotaryIsNull(RequestStatus.PROCESSING, pageable);
        }
        return notaryRequestRepository.findByNotaryUserIdAndStatus(notaryUserId, status, pageable);
    }

    public Page<NotaryRequest> listForNotaryByStatus(UUID notaryUserId, RequestStatus status, org.springframework.data.domain.PageRequest pageRequest) {
        return listForNotaryByStatus(notaryUserId, status, (Pageable) pageRequest);
    }

    public Page<NotaryRequest> listAcceptedByNotary(UUID notaryUserId, Pageable pageable) {
        return notaryRequestRepository.findByNotaryUserId(notaryUserId, pageable);
    }

    public List<AppointmentResponse> getMyAppointments(UUID notaryUserId) {
        return appointmentRepository.findByRequestNotaryUserIdOrderByScheduledTimeAsc(notaryUserId)
                .stream()
                .map(AppointmentResponse::fromEntity)
                .toList();
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

    public Path getProjectRootPublic() {
        return findProjectRoot();
    }

    public Path resolveStoredFilePath(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            throw new AppException(ErrorCode.DOCUMENT_NOT_FOUND);
        }

        Path rawPath = Path.of(storedPath);
        Path projectRoot = findProjectRoot();
        List<Path> roots = candidateStorageRoots(projectRoot);

        if (rawPath.isAbsolute()) {
            Path normalized = rawPath.normalize();
            boolean allowed = roots.stream()
                    .map(root -> root.resolve("uploads").normalize())
                    .anyMatch(normalized::startsWith);
            if (!allowed) {
                throw new AppException("ÄÆ°á»ng dáº«n tÃ i liá»‡u khÃ´ng há»£p lá»‡", HttpStatus.BAD_REQUEST);
            }
            return normalized;
        }

        Path fallback = null;
        for (Path root : roots) {
            Path candidate = root.resolve(rawPath).normalize();
            if (!candidate.startsWith(root)) {
                continue;
            }
            if (fallback == null) {
                fallback = candidate;
            }
            if (Files.exists(candidate)) {
                return candidate;
            }
        }

        if (fallback != null) {
            return fallback;
        }
        throw new AppException("ÄÆ°á»ng dáº«n tÃ i liá»‡u khÃ´ng há»£p lá»‡", HttpStatus.BAD_REQUEST);
    }

    private List<Path> candidateStorageRoots(Path projectRoot) {
        List<Path> roots = new java.util.ArrayList<>();
        roots.add(projectRoot);

        Path parent = projectRoot.getParent();
        if (parent != null && "backend".equalsIgnoreCase(projectRoot.getFileName().toString())) {
            roots.add(parent);
        }

        Path backendRoot = projectRoot.resolve("backend").normalize();
        if (Files.exists(backendRoot.resolve("pom.xml"))) {
            roots.add(backendRoot);
        }

        return roots.stream().distinct().toList();
    }

    private boolean isUnderUploadsRoot(Path file) {
        Path normalized = file.normalize();
        return candidateStorageRoots(findProjectRoot()).stream()
                .map(root -> root.resolve("uploads").normalize())
                .anyMatch(normalized::startsWith);
    }

    public List<Document> getDocumentsByRequestId(UUID requestId) {
        return documentRepository.findByRequest_RequestId(requestId);
    }

    @Transactional
    public Document uploadDocument(UUID requestId, String uploaderEmail, MultipartFile file, String docType) {
        NotaryRequest request = getById(requestId);
        DocumentType documentType = documentTypeService.getActiveForUpload(docType);

        User uploader = userRepository.findByEmail(uploaderEmail)
                .orElseThrow(() -> new AppException("Không tìm thấy người dùng", HttpStatus.NOT_FOUND));

        boolean isOwner = request.getClient() != null && request.getClient().getUserId().equals(uploader.getUserId());
        boolean isAssignedNotary = request.getNotary() != null && request.getNotary().getUserId().equals(uploader.getUserId());
        boolean isAdmin = uploader.getRole() != null && uploader.getRole().name().equals("ADMIN");
        boolean isUnassignedProcessingDraftUpload = uploader.getRole() != null
                && uploader.getRole().name().equals("NOTARY")
                && request.getNotary() == null
                && request.getStatus() == RequestStatus.PROCESSING
                && DRAFT_CONTRACT_DOC_TYPE.equals(documentType.getCode());

        if (!isOwner && !isAssignedNotary && !isAdmin && !isUnassignedProcessingDraftUpload) {
            throw new AppException("Không có quyền upload hồ sơ cho yêu cầu này", HttpStatus.FORBIDDEN);
        }

        validateRequestIsNotTerminal(request);

        StoredFileResult stored = storeFile(file, requestId, documentType);

        Document doc = new Document();
        doc.setRequest(request);
        doc.setFilePath(stored.relativePath());
        doc.setOriginalFileName(stored.originalFileName());
        doc.setContentType(stored.contentType());
        doc.setFileSize(stored.fileSize());
        doc.setDocType(documentType.getCode());
        doc.setFileHash(stored.hash());
        doc.setCreatedAt(OffsetDateTime.now());

        Document saved = documentRepository.save(doc);
        syncRequestStatusAfterRequiredDocumentsCompleted(request);
        return saved;
    }

    @Transactional
    public Document replaceDocument(UUID documentId, String uploaderEmail, MultipartFile file) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));

        NotaryRequest request = doc.getRequest();
        User uploader = userRepository.findByEmail(uploaderEmail)
                .orElseThrow(() -> new AppException("Không tìm thấy người dùng", HttpStatus.NOT_FOUND));

        boolean isOwner = request.getClient() != null && request.getClient().getUserId().equals(uploader.getUserId());
        boolean isAssignedNotary = request.getNotary() != null && request.getNotary().getUserId().equals(uploader.getUserId());
        boolean isAdmin = uploader.getRole() != null && uploader.getRole().name().equals("ADMIN");

        if (!isOwner && !isAssignedNotary && !isAdmin) {
            throw new AppException("Không có quyền cập nhật tài liệu cho yêu cầu này", HttpStatus.FORBIDDEN);
        }

        validateRequestIsNotTerminal(request);
        validateDocumentCanBeReplaced(doc);

        String oldFilePath = doc.getFilePath();
        DocumentType documentType = documentTypeService.getActiveForUpload(doc.getDocType());
        StoredFileResult stored = storeFile(file, request.getRequestId(), documentType);
        doc.setFilePath(stored.relativePath());
        doc.setOriginalFileName(stored.originalFileName());
        doc.setContentType(stored.contentType());
        doc.setFileSize(stored.fileSize());
        doc.setFileHash(stored.hash());
        doc.setUpdatedAt(OffsetDateTime.now());
        Document saved = documentRepository.save(doc);
        deleteStoredFile(oldFilePath);
        syncRequestStatusAfterRequiredDocumentsCompleted(request);
        return saved;
    }

    private void syncRequestStatusAfterRequiredDocumentsCompleted(NotaryRequest request) {
        if (request.getStatus() != RequestStatus.NEW) {
            return;
        }

        DocumentRequirementResponse documentRequirements = buildClientDocumentRequirementsForProcessing(request);
        if (!documentRequirements.isReadyForAccept()) {
            return;
        }

        request.setStatus(RequestStatus.PROCESSING);
        request.setUpdatedAt(OffsetDateTime.now());
        notaryRequestRepository.save(request);
    }

    private StoredFileResult storeFile(MultipartFile file, UUID requestId, DocumentType documentType) {
        if (file == null || file.isEmpty()) {
            throw new AppException("File tải lên không hợp lệ", HttpStatus.BAD_REQUEST);
        }

        if (file.getSize() > MAX_DOCUMENT_FILE_SIZE_BYTES) {
            throw new AppException("Dung lượng file không được vượt quá 10MB", HttpStatus.BAD_REQUEST);
        }

        try {
            Path projectRoot = findProjectRoot();
            Path uploadsDir = projectRoot.resolve("uploads").resolve("requests").resolve(requestId.toString()).normalize();
            Files.createDirectories(uploadsDir);

            String originalName = file.getOriginalFilename() == null ? "document.bin" : file.getOriginalFilename();
            String safeName = Path.of(originalName).getFileName().toString();
            if (safeName.isBlank()) {
                safeName = "document.bin";
            }

            validateDocumentFileType(file, safeName, documentType);

            String filename = UUID.randomUUID() + "-" + safeName;
            Path target = uploadsDir.resolve(filename).normalize();
            if (!target.startsWith(uploadsDir)) {
                throw new AppException("Tên file không hợp lệ", HttpStatus.BAD_REQUEST);
            }

            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            byte[] bytes = Files.readAllBytes(target);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            String hash = HexFormat.of().formatHex(digest);

            Path relative = projectRoot.relativize(target);
            return new StoredFileResult(
                    relative.toString().replace("\\", "/"),
                    safeName,
                    normalizeContentType(file.getContentType()),
                    file.getSize(),
                    hash
            );
        } catch (AppException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new AppException("Lỗi khi lưu file", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception ex) {
            throw new AppException("Lỗi khi tính toán hash file", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void validateDocumentFileType(MultipartFile file, String safeName, DocumentType documentType) {
        String extension = getExtension(safeName);
        String contentType = normalizeContentType(file.getContentType());

        if (DocumentTypeService.FILE_GROUP_VIDEO.equals(documentType.getAllowedFileGroup())) {
            if (!ALLOWED_VIDEO_EXTENSIONS.contains(extension)) {
                throw new AppException("Định dạng video không được hỗ trợ. Chỉ chấp nhận MP4, WEBM, MOV", HttpStatus.BAD_REQUEST);
            }
            if (!ALLOWED_VIDEO_CONTENT_TYPES.contains(contentType)) {
                throw new AppException("MIME type của video không hợp lệ", HttpStatus.BAD_REQUEST);
            }
            return;
        }

        if (DocumentTypeService.FILE_GROUP_IMAGE.equals(documentType.getAllowedFileGroup())) {
            if (!(extension.equals("jpg") || extension.equals("jpeg") || extension.equals("png"))) {
                throw new AppException("Định dạng ảnh không được hỗ trợ. Chỉ chấp nhận JPG, JPEG, PNG", HttpStatus.BAD_REQUEST);
            }
            if (!Set.of("image/jpeg", "image/png", "application/octet-stream").contains(contentType)) {
                throw new AppException("MIME type của ảnh không hợp lệ", HttpStatus.BAD_REQUEST);
            }
            return;
        }

        if (!ALLOWED_DOCUMENT_EXTENSIONS.contains(extension)) {
            throw new AppException("Định dạng file không được hỗ trợ. Chỉ chấp nhận PDF, DOC, DOCX, JPG, JPEG, PNG", HttpStatus.BAD_REQUEST);
        }

        if (!ALLOWED_DOCUMENT_CONTENT_TYPES.contains(contentType)) {
            throw new AppException("MIME type của file không hợp lệ", HttpStatus.BAD_REQUEST);
        }

        if (extension.equals("pdf") && !contentType.equals("application/pdf") && !contentType.equals("application/octet-stream")) {
            throw new AppException("Nội dung file không khớp định dạng PDF", HttpStatus.BAD_REQUEST);
        }
        if ((extension.equals("jpg") || extension.equals("jpeg")) && !contentType.equals("image/jpeg") && !contentType.equals("application/octet-stream")) {
            throw new AppException("Nội dung file không khớp định dạng JPG/JPEG", HttpStatus.BAD_REQUEST);
        }
        if (extension.equals("png") && !contentType.equals("image/png") && !contentType.equals("application/octet-stream")) {
            throw new AppException("Nội dung file không khớp định dạng PNG", HttpStatus.BAD_REQUEST);
        }
        if (extension.equals("doc") && !contentType.equals("application/msword") && !contentType.equals("application/octet-stream")) {
            throw new AppException("Nội dung file không khớp định dạng DOC", HttpStatus.BAD_REQUEST);
        }
        if (extension.equals("docx")
                && !contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                && !contentType.equals("application/octet-stream")) {
            throw new AppException("Nội dung file không khớp định dạng DOCX", HttpStatus.BAD_REQUEST);
        }
    }

    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeContentType(String contentType) {
        return contentType == null || contentType.isBlank()
                ? "application/octet-stream"
                : contentType.toLowerCase(Locale.ROOT).trim();
    }

    private void deleteStoredFile(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }

        try {
            Path file = resolveStoredFilePath(relativePath);
            if (isUnderUploadsRoot(file)) {
                Files.deleteIfExists(file);
            }
        } catch (IOException ignored) {
            // Replacement is saved; stale-file cleanup should not fail the user action.
        }
    }

    private void validateRequestIsNotTerminal(NotaryRequest request) {
        if (request.getStatus() == RequestStatus.REJECTED
                || request.getStatus() == RequestStatus.CANCELLED
                || request.getStatus() == RequestStatus.COMPLETED
                || request.getStatus() == RequestStatus.IN_VIDEO_CALL) {
            throw new AppException(
                    ErrorCode.REQUEST_TERMINAL_STATUS,
                    Map.of("status", request.getStatus().name())
            );
        }
    }

    private void validateDocumentCanBeReplaced(Document doc) {
        String type = doc.getDocType();
        Set<String> nonReplaceableTypes = Set.of(DocumentTypeService.SIGNED_DOCUMENT, DocumentTypeService.EVIDENCE_PHOTO);

        if (nonReplaceableTypes.contains(type)) {
            throw new AppException(
                    ErrorCode.DOCUMENT_REPLACE_NOT_ALLOWED,
                    Map.of("docType", type)
            );
        }

        if (DocumentTypeService.SESSION_VIDEO.equals(type)) {
            List<String> requestDocTypes = documentRepository.findDocTypesByRequestId(doc.getRequest().getRequestId());
            if (requestDocTypes.contains(DocumentTypeService.SIGNED_DOCUMENT)) {
                throw new AppException(
                        ErrorCode.DOCUMENT_REPLACE_NOT_ALLOWED,
                        Map.of("docType", type, "reason", "SIGNED_DOCUMENT_EXISTS")
                );
            }
        }
    }

    private record StoredFileResult(String relativePath, String originalFileName, String contentType, Long fileSize, String hash) {
    }

    @Transactional
    public NotaryRequest cancelRequest(UUID requestId, String requesterEmail) {
        NotaryRequest request = getById(requestId);

        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new AppException("Không tìm thấy người dùng", HttpStatus.NOT_FOUND));

        boolean isOwner = request.getClient() != null && request.getClient().getUserId().equals(requester.getUserId());
        boolean isAdmin = requester.getRole() != null && requester.getRole().name().equals("ADMIN");

        if (!isOwner && !isAdmin) {
            throw new AppException("Không có quyền hủy yêu cầu này", HttpStatus.FORBIDDEN);
        }

        if (request.getStatus() == RequestStatus.COMPLETED) {
            throw new AppException("Không thể hủy yêu cầu đã hoàn thành", HttpStatus.BAD_REQUEST);
        }

        if (request.getStatus() == RequestStatus.IN_VIDEO_CALL) {
            throw new AppException("Không thể hủy yêu cầu đang trong phiên xác thực danh tính", HttpStatus.BAD_REQUEST);
        }

        request.setStatus(RequestStatus.CANCELLED);
        request.setUpdatedAt(OffsetDateTime.now());
        return notaryRequestRepository.save(request);
    }

    @Transactional
    public AppointmentResponse scheduleAppointment(UUID requestId, String notaryEmail, ScheduleAppointmentRequest req) {
        NotaryRequest request = getById(requestId);

        User reviewer = userRepository.findByEmail(notaryEmail)
                .orElseThrow(() -> new AppException("Không tìm thấy người dùng", HttpStatus.NOT_FOUND));

        boolean isAdmin = reviewer.getRole() != null && reviewer.getRole().name().equals("ADMIN");
        boolean isAssignedNotary = reviewer.getRole() != null
                && reviewer.getRole().name().equals("NOTARY")
                && request.getNotary() != null
                && request.getNotary().getUserId().equals(reviewer.getUserId());

        if (isClaimedByAnotherNotary(request, reviewer)) {
            throw alreadyAssignedException();
        }

        if (!isAdmin && !isAssignedNotary) {
            throw new AppException("Không có quyền lên lịch cho yêu cầu này", HttpStatus.FORBIDDEN);
        }

        if (request.getStatus() != RequestStatus.ACCEPTED) {
            throw new AppException(
                    "Chỉ có thể lên lịch khi yêu cầu đang ở trạng thái ACCEPTED (hiện tại: " + request.getStatus() + ")",
                    HttpStatus.BAD_REQUEST);
        }

        if (req.getScheduledTime() == null || !req.getScheduledTime().isAfter(OffsetDateTime.now())) {
            throw new AppException("Thời gian hẹn phải ở tương lai", HttpStatus.BAD_REQUEST);
        }

        if (appointmentRepository.existsByRequestRequestId(requestId)) {
            throw new AppException("Yêu cầu này đã có lịch hẹn", HttpStatus.CONFLICT);
        }

        Appointment appointment = new Appointment();
        appointment.setRequest(request);
        appointment.setScheduledTime(req.getScheduledTime());
        appointment.setStatus(AppointmentStatus.PENDING);
        appointment.setCreatedAt(OffsetDateTime.now());

        if (request.getServiceType() == ServiceType.OFFLINE) {
            String address = (req.getPhysicalAddress() != null && !req.getPhysicalAddress().isBlank())
                    ? req.getPhysicalAddress()
                    : "Văn phòng công chứng số 1";
            appointment.setPhysicalAddress(address);
            appointment.setMeetingUrl(null);
        } else {
            appointment.setPhysicalAddress(null);
        }

        Appointment saved = appointmentRepository.save(appointment);

        if (request.getServiceType() == ServiceType.ONLINE) {
            VideoSession session = new VideoSession();
            session.setAppointment(saved);

            String roomId = "room_" + UUID.randomUUID().toString().substring(0, 8);
            String sessionToken = UUID.randomUUID().toString();

            session.setRoomId(roomId);
            session.setSessionToken(sessionToken);

            String meetingUrl = baseUrl + "/api/video/room/" + roomId + "?token=" + sessionToken;
            session.setMeetingUrl(meetingUrl);
            session.setStatus(VideoSessionStatus.PENDING);
            session.setCreatedAt(OffsetDateTime.now());
            session.setUpdatedAt(OffsetDateTime.now());

            videoSessionRepository.save(session);

            saved.setMeetingUrl(meetingUrl);
            appointmentRepository.save(saved);
        }

         request.setStatus(RequestStatus.SCHEDULED);
         request.setUpdatedAt(OffsetDateTime.now());
         notaryRequestRepository.save(request);
         notificationService.createAppointmentScheduledNotification(request, saved);

         return AppointmentResponse.fromEntity(saved);
    }

    @Transactional
    public NotaryRequest rejectRequest(UUID requestId, String reviewerEmail, String reason) {
        NotaryRequest request = getById(requestId);

        User reviewer = userRepository.findByEmail(reviewerEmail)
                .orElseThrow(() -> new AppException("Không tìm thấy người dùng", HttpStatus.NOT_FOUND));

        boolean isAdmin = reviewer.getRole() != null && reviewer.getRole().name().equals("ADMIN");
        boolean isAssignedNotary = reviewer.getRole() != null
                && reviewer.getRole().name().equals("NOTARY")
                && request.getNotary() != null
                && request.getNotary().getUserId().equals(reviewer.getUserId());

        if (isClaimedByAnotherNotary(request, reviewer)) {
            throw alreadyAssignedException();
        }

        if (!isAdmin && !isAssignedNotary) {
            throw new AppException("Không có quyền từ chối yêu cầu này", HttpStatus.FORBIDDEN);
        }

        if (request.getStatus() == RequestStatus.COMPLETED
                || request.getStatus() == RequestStatus.CANCELLED
                || request.getStatus() == RequestStatus.REJECTED) {
            throw new AppException("Không thể từ chối yêu cầu ở trạng thái hiện tại", HttpStatus.BAD_REQUEST);
        }

        request.setStatus(RequestStatus.REJECTED);
        request.setRejectionReason(reason == null ? null : reason.trim());
        request.setUpdatedAt(OffsetDateTime.now());
        return notaryRequestRepository.save(request);
    }

    private AppException alreadyAssignedException() {
        return new AppException(ErrorCode.REQUEST_ALREADY_ASSIGNED);
    }
}
