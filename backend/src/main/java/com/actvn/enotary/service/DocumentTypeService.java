package com.actvn.enotary.service;

import com.actvn.enotary.dto.request.DocumentTypeRequest;
import com.actvn.enotary.entity.DocumentType;
import com.actvn.enotary.exception.AppException;
import com.actvn.enotary.repository.DocumentTypeRepository;
import com.actvn.enotary.repository.NotaryServiceDocumentRequirementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DocumentTypeService {
    public static final String SOURCE_USER_UPLOAD = "USER_UPLOAD";
    public static final String SOURCE_SYSTEM_GENERATED = "SYSTEM_GENERATED";
    public static final String SOURCE_INTERNAL = "INTERNAL";

    public static final String FILE_GROUP_DOCUMENT = "DOCUMENT";
    public static final String FILE_GROUP_IMAGE = "IMAGE";
    public static final String FILE_GROUP_VIDEO = "VIDEO";
    public static final String FILE_GROUP_ANY = "ANY";

    public static final String REQUEST_FORM = "REQUEST_FORM";
    public static final String SIGNED_DOCUMENT = "SIGNED_DOCUMENT";
    public static final String SESSION_VIDEO = "SESSION_VIDEO";
    public static final String EVIDENCE_PHOTO = "EVIDENCE_PHOTO";

    private static final Set<String> VALID_SOURCES = Set.of(SOURCE_USER_UPLOAD, SOURCE_SYSTEM_GENERATED, SOURCE_INTERNAL);
    private static final Set<String> VALID_FILE_GROUPS = Set.of(FILE_GROUP_DOCUMENT, FILE_GROUP_IMAGE, FILE_GROUP_VIDEO, FILE_GROUP_ANY);

    private final DocumentTypeRepository documentTypeRepository;
    private final NotaryServiceDocumentRequirementRepository requirementRepository;

    public List<DocumentType> listAll() {
        return documentTypeRepository.findAllByOrderBySortOrderAscCodeAsc();
    }

    public List<DocumentType> listActive() {
        return documentTypeRepository.findByIsActiveTrueOrderBySortOrderAscCodeAsc();
    }

    public DocumentType getActiveForUpload(String code) {
        DocumentType documentType = getByCode(code);
        if (!Boolean.TRUE.equals(documentType.getIsActive())) {
            throw new AppException("Loại hồ sơ đã ngừng áp dụng", HttpStatus.BAD_REQUEST);
        }
        if (!SOURCE_USER_UPLOAD.equals(documentType.getSource()) && !SOURCE_INTERNAL.equals(documentType.getSource())) {
            throw new AppException("Loại hồ sơ này không cho phép tải file lên", HttpStatus.BAD_REQUEST);
        }
        return documentType;
    }

    public DocumentType getByCode(String code) {
        return documentTypeRepository.findById(normalizeCode(code))
                .orElseThrow(() -> new AppException("Không tìm thấy loại hồ sơ", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public DocumentType create(DocumentTypeRequest request) {
        String code = normalizeCode(request.getCode());
        if (documentTypeRepository.existsById(code)) {
            throw new AppException("Mã loại hồ sơ đã tồn tại", HttpStatus.BAD_REQUEST);
        }
        DocumentType documentType = new DocumentType();
        documentType.setCode(code);
        documentType.setIsSystem(false);
        applyRequest(documentType, request);
        return documentTypeRepository.save(documentType);
    }

    @Transactional
    public DocumentType update(String code, DocumentTypeRequest request) {
        DocumentType documentType = getByCode(code);
        if (Boolean.TRUE.equals(documentType.getIsSystem())) {
            documentType.setName(request.getName());
            documentType.setDescription(request.getDescription());
            Boolean nextActive = request.getIsActive() != null ? request.getIsActive() : documentType.getIsActive();
            if (!Boolean.TRUE.equals(nextActive) && requirementRepository.existsByDocType(documentType.getCode())) {
                throw new AppException("Không thể ngừng áp dụng loại hồ sơ đang được dùng trong cấu hình bắt buộc", HttpStatus.BAD_REQUEST);
            }
            documentType.setIsActive(nextActive);
            documentType.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : documentType.getSortOrder());
            return documentTypeRepository.save(documentType);
        }

        applyRequest(documentType, request);
        return documentTypeRepository.save(documentType);
    }

    private void applyRequest(DocumentType documentType, DocumentTypeRequest request) {
        String source = normalizeEnumValue(request.getSource(), SOURCE_USER_UPLOAD);
        String fileGroup = normalizeEnumValue(request.getAllowedFileGroup(), FILE_GROUP_DOCUMENT);
        if (!VALID_SOURCES.contains(source)) {
            throw new AppException("Nguồn loại hồ sơ không hợp lệ", HttpStatus.BAD_REQUEST);
        }
        if (!VALID_FILE_GROUPS.contains(fileGroup)) {
            throw new AppException("Nhóm file không hợp lệ", HttpStatus.BAD_REQUEST);
        }
        if (SOURCE_INTERNAL.equals(source)) {
            throw new AppException("Không thể tạo loại hồ sơ nội bộ từ giao diện quản trị", HttpStatus.BAD_REQUEST);
        }

        documentType.setName(request.getName());
        documentType.setDescription(request.getDescription());
        documentType.setSource(source);
        documentType.setAllowedFileGroup(fileGroup);
        Boolean nextActive = request.getIsActive() != null ? request.getIsActive() : true;
        if (!Boolean.TRUE.equals(nextActive) && requirementRepository.existsByDocType(documentType.getCode())) {
            throw new AppException("Không thể ngừng áp dụng loại hồ sơ đang được dùng trong cấu hình bắt buộc", HttpStatus.BAD_REQUEST);
        }
        documentType.setIsActive(nextActive);
        documentType.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeEnumValue(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim().toUpperCase(Locale.ROOT);
    }
}
