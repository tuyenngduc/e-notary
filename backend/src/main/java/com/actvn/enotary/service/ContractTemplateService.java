package com.actvn.enotary.service;

import com.actvn.enotary.entity.ContractTemplate;
import com.actvn.enotary.entity.NotaryServiceType;
import com.actvn.enotary.exception.AppException;
import com.actvn.enotary.repository.ContractTemplateRepository;
import com.actvn.enotary.repository.NotaryServiceTypeRepository;
import com.actvn.enotary.repository.NotaryRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContractTemplateService {
    private final ContractTemplateRepository contractTemplateRepository;
    private final NotaryServiceTypeRepository notaryServiceTypeRepository;
    private final NotaryRequestRepository notaryRequestRepository;

    private static final String UPLOAD_DIR = "uploads/templates/";

    public Page<ContractTemplate> getAll(Pageable pageable) {
        return contractTemplateRepository.findAll(pageable);
    }

    public ContractTemplate getById(UUID id) {
        return contractTemplateRepository.findById(id)
                .orElseThrow(() -> new AppException("Không tìm thấy mẫu hợp đồng", HttpStatus.NOT_FOUND));
    }

    public List<ContractTemplate> getActiveTemplates() {
        return contractTemplateRepository.findByIsActiveTrue();
    }

    public List<ContractTemplate> getByServiceType(UUID serviceTypeId, boolean onlyActive) {
        if (onlyActive) {
            return contractTemplateRepository.findByServiceTypeIdAndIsActiveTrue(serviceTypeId);
        }
        return contractTemplateRepository.findByServiceTypeId(serviceTypeId);
    }

    public ContractTemplate createTemplate(UUID serviceTypeId, String name, String version, MultipartFile file) {
        NotaryServiceType serviceType = notaryServiceTypeRepository.findById(serviceTypeId)
                .orElseThrow(() -> new AppException("Không tìm thấy loại dịch vụ", HttpStatus.NOT_FOUND));

        String fileUrl = saveFile(file);

        ContractTemplate template = new ContractTemplate();
        template.setServiceType(serviceType);
        template.setName(name);
        template.setVersion(version);
        template.setFileUrl(fileUrl);
        template.setIsActive(true);

        return contractTemplateRepository.save(template);
    }

    public ContractTemplate updateTemplate(UUID id, String name, String version, Boolean isActive, MultipartFile file) {
        ContractTemplate existing = contractTemplateRepository.findById(id)
                .orElseThrow(() -> new AppException("Không tìm thấy mẫu hợp đồng", HttpStatus.NOT_FOUND));

        if (name != null) {
            existing.setName(name);
        }
        if (version != null) {
            existing.setVersion(version);
        }
        if (isActive != null) {
            existing.setIsActive(isActive);
        }
        
        if (file != null && !file.isEmpty()) {
            String fileUrl = saveFile(file);
            existing.setFileUrl(fileUrl);
        }

        return contractTemplateRepository.save(existing);
    }

    @Transactional
    public void deleteTemplate(UUID id) {
        ContractTemplate existing = contractTemplateRepository.findById(id)
                .orElseThrow(() -> new AppException("Không tìm thấy mẫu hợp đồng", HttpStatus.NOT_FOUND));

        notaryRequestRepository.clearSelectedTemplate(id);
        deleteFileIfExists(existing.getFileUrl());
        contractTemplateRepository.delete(existing);
    }

    private void deleteFileIfExists(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }
        try {
            Path filePath = Paths.get(fileUrl).normalize();
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new AppException("Lỗi khi xóa file mẫu hợp đồng", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String saveFile(MultipartFile file) {
        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            String fileName = UUID.randomUUID().toString() + extension;
            Path filePath = uploadPath.resolve(fileName);
            
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            
            return UPLOAD_DIR + fileName;
        } catch (IOException e) {
            throw new AppException("Lỗi khi lưu file mẫu hợp đồng", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
