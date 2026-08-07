package com.actvn.enotary.service;

import com.actvn.enotary.dto.response.DocumentRequirementConfigResponse;
import com.actvn.enotary.dto.request.DocumentRequirementConfigRequest;
import com.actvn.enotary.entity.DocumentType;
import com.actvn.enotary.entity.NotaryServiceDocumentRequirement;
import com.actvn.enotary.entity.NotaryServiceType;
import com.actvn.enotary.exception.AppException;
import com.actvn.enotary.repository.DocumentTypeRepository;
import com.actvn.enotary.repository.NotaryServiceDocumentRequirementRepository;
import com.actvn.enotary.repository.NotaryServiceTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentRequirementConfigService {
    private final NotaryServiceTypeRepository serviceTypeRepository;
    private final NotaryServiceDocumentRequirementRepository requirementRepository;
    private final DocumentTypeRepository documentTypeRepository;

    public List<DocumentRequirementConfigResponse> listAll() {
        return serviceTypeRepository.findAll().stream()
                .map(serviceType -> DocumentRequirementConfigResponse.fromEntity(
                        serviceType,
                        requirementRepository.findDocTypesByServiceCode(serviceType.getServiceCode())
                ))
                .toList();
    }

    @Transactional
    public DocumentRequirementConfigResponse create(DocumentRequirementConfigRequest request) {
        String serviceCode = normalizeServiceCode(request.getServiceCode());
        if (serviceTypeRepository.findByServiceCode(serviceCode).isPresent()) {
            throw new AppException("Mã dịch vụ đã tồn tại", HttpStatus.BAD_REQUEST);
        }

        List<String> normalizedDocTypes = normalizeRequiredDocTypes(request.getRequiredDocTypes());
        NotaryServiceType serviceType = new NotaryServiceType();
        applyServiceRequest(serviceType, request, serviceCode);
        NotaryServiceType saved = serviceTypeRepository.save(serviceType);
        saveRequirements(saved, normalizedDocTypes);
        return DocumentRequirementConfigResponse.fromEntity(saved, normalizedDocTypes);
    }

    @Transactional
    public DocumentRequirementConfigResponse update(UUID serviceId, DocumentRequirementConfigRequest request) {
        NotaryServiceType serviceType = serviceTypeRepository.findById(serviceId)
                .orElseThrow(() -> new AppException("Không tìm thấy dịch vụ công chứng", HttpStatus.NOT_FOUND));

        String serviceCode = normalizeServiceCode(request.getServiceCode());
        serviceTypeRepository.findByServiceCode(serviceCode)
                .filter(existing -> !existing.getId().equals(serviceId))
                .ifPresent(existing -> {
                    throw new AppException("Mã dịch vụ đã tồn tại", HttpStatus.BAD_REQUEST);
                });

        List<String> normalized = normalizeRequiredDocTypes(request.getRequiredDocTypes());
        applyServiceRequest(serviceType, request, serviceCode);
        requirementRepository.deleteByServiceType_Id(serviceId);
        requirementRepository.flush();
        saveRequirements(serviceType, normalized);
        return DocumentRequirementConfigResponse.fromEntity(serviceTypeRepository.save(serviceType), normalized);
    }

    @Transactional
    public void delete(UUID serviceId) {
        NotaryServiceType serviceType = serviceTypeRepository.findById(serviceId)
                .orElseThrow(() -> new AppException("Không tìm thấy dịch vụ công chứng", HttpStatus.NOT_FOUND));
        serviceType.setIsActive(false);
        serviceTypeRepository.save(serviceType);
    }

    @Transactional
    public void ensureDefaultRequirements(NotaryServiceType serviceType) {
        if (!requirementRepository.findByServiceType_IdOrderBySortOrderAscDocTypeAsc(serviceType.getId()).isEmpty()) {
            return;
        }
        saveRequirements(serviceType, List.of("ID_CARD", "DRAFT_CONTRACT"));
    }

    private void saveRequirements(NotaryServiceType serviceType, List<String> docTypes) {
        List<NotaryServiceDocumentRequirement> requirements = new ArrayList<>();
        for (int index = 0; index < docTypes.size(); index++) {
            NotaryServiceDocumentRequirement requirement = new NotaryServiceDocumentRequirement();
            requirement.setServiceType(serviceType);
            requirement.setDocType(docTypes.get(index));
            requirement.setSortOrder((index + 1) * 10);
            requirements.add(requirement);
        }
        requirementRepository.saveAll(requirements);
    }

    private List<String> normalizeRequiredDocTypes(List<String> docTypes) {
        if (docTypes == null || docTypes.isEmpty()) {
            throw new AppException("Danh sách giấy tờ bắt buộc không được để trống", HttpStatus.BAD_REQUEST);
        }

        Set<String> unique = new LinkedHashSet<>();
        for (String docType : docTypes) {
            String normalized = docType == null ? "" : docType.trim().toUpperCase();
            DocumentType documentType = documentTypeRepository.findById(normalized)
                    .orElseThrow(() -> new AppException("Loại giấy tờ bắt buộc không hợp lệ", HttpStatus.BAD_REQUEST));
            if (!Boolean.TRUE.equals(documentType.getIsActive())
                    || DocumentTypeService.SOURCE_INTERNAL.equals(documentType.getSource())) {
                throw new AppException("Loại giấy tờ bắt buộc không hợp lệ", HttpStatus.BAD_REQUEST);
            }
            if (!unique.add(normalized)) {
                throw new AppException("Danh sách giấy tờ bắt buộc không được trùng lặp", HttpStatus.BAD_REQUEST);
            }
        }

        return new ArrayList<>(unique);
    }

    private void applyServiceRequest(
            NotaryServiceType serviceType,
            DocumentRequirementConfigRequest request,
            String serviceCode) {
        serviceType.setServiceCode(serviceCode);
        serviceType.setName(request.getServiceName().trim());
        serviceType.setBasePrice(request.getBasePrice());
        serviceType.setDescription(request.getDescription());
        serviceType.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        serviceType.setRequiresTemplate(request.getRequiresTemplate() != null ? request.getRequiresTemplate() : true);
    }

    private String normalizeServiceCode(String serviceCode) {
        return serviceCode == null ? "" : serviceCode.trim().toUpperCase(Locale.ROOT);
    }
}
