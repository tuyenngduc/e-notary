package com.actvn.enotary.controller;

import com.actvn.enotary.dto.response.ApiResponse;
import com.actvn.enotary.dto.response.ApiResponseUtil;
import com.actvn.enotary.dto.response.ContractTemplateResponse;
import com.actvn.enotary.entity.ContractTemplate;
import com.actvn.enotary.exception.AppException;
import com.actvn.enotary.exception.ErrorCode;
import com.actvn.enotary.security.CustomUserDetails;
import com.actvn.enotary.service.ContractTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.PathResource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ContractTemplateController {
    private final ContractTemplateService contractTemplateService;

    // --- PUBLIC APIS ---

    @GetMapping("/api/templates")
    public ResponseEntity<ApiResponse<List<ContractTemplateResponse>>> getTemplates(
            @RequestParam(required = false) UUID serviceTypeId,
            @RequestParam(defaultValue = "true") boolean onlyActive) {
        
        List<ContractTemplateResponse> templates;
        if (serviceTypeId != null) {
            templates = contractTemplateService.getByServiceType(serviceTypeId, onlyActive)
                    .stream().map(ContractTemplateResponse::fromEntity).toList();
        } else {
            List<ContractTemplate> templateEntities = onlyActive
                    ? contractTemplateService.getActiveTemplates()
                    : contractTemplateService.getAll(Pageable.unpaged()).toList();
            templates = templateEntities.stream()
                    .map(ContractTemplateResponse::fromEntity)
                    .toList();
        }
        
        return ResponseEntity.ok(ApiResponseUtil.success(templates, "Lấy danh sách mẫu thành công"));
    }

    @GetMapping("/api/templates/{id}/download")
    public ResponseEntity<?> downloadTemplate(@PathVariable UUID id) {
        return serveTemplateFile(id, false);
    }

    @GetMapping("/api/templates/{id}/view")
    public ResponseEntity<?> viewTemplate(@PathVariable UUID id) {
        return serveTemplateFile(id, true);
    }

    private ResponseEntity<?> serveTemplateFile(UUID id, boolean inline) {
        ContractTemplate template = contractTemplateService.getById(id);

        try {
            Path file = Paths.get(template.getFileUrl()).normalize();
            if (!Files.exists(file)) {
                throw new AppException("File không tồn tại trên hệ thống", HttpStatus.NOT_FOUND);
            }

            String contentType = Files.probeContentType(file);
            if (contentType == null) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }

            PathResource resource = new PathResource(file);
            String disposition = (inline ? "inline" : "attachment") + "; filename=\"" + file.getFileName().toString() + "\"";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        } catch (IOException ex) {
            throw new AppException("Lỗi khi tải file mẫu", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // --- ADMIN APIS ---

    @PostMapping(value = "/api/admin/templates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ContractTemplateResponse>> createTemplate(
            Authentication authentication,
            @RequestParam("serviceTypeId") UUID serviceTypeId,
            @RequestParam("name") String name,
            @RequestParam(value = "version", required = false) String version,
            @RequestParam("file") MultipartFile file) {
        ensureAdmin(authentication);
        
        ContractTemplate created = contractTemplateService.createTemplate(serviceTypeId, name, version, file);
        return ResponseEntity.ok(ApiResponseUtil.success(ContractTemplateResponse.fromEntity(created), "Thêm mẫu hợp đồng thành công"));
    }

    @PutMapping(value = "/api/admin/templates/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ContractTemplateResponse>> updateTemplate(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "version", required = false) String version,
            @RequestParam(value = "isActive", required = false) Boolean isActive,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        ensureAdmin(authentication);
        
        ContractTemplate updated = contractTemplateService.updateTemplate(id, name, version, isActive, file);
        return ResponseEntity.ok(ApiResponseUtil.success(ContractTemplateResponse.fromEntity(updated), "Cập nhật mẫu hợp đồng thành công"));
    }

    @DeleteMapping("/api/admin/templates/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTemplate(
            Authentication authentication,
            @PathVariable UUID id) {
        ensureAdmin(authentication);
        contractTemplateService.deleteTemplate(id);
        return ResponseEntity.ok(ApiResponseUtil.success(null, "Xóa mẫu hợp đồng thành công"));
    }

    private void ensureAdmin(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new AppException(ErrorCode.INVALID_AUTHENTICATION);
        }
        String role = userDetails.getRole() != null ? userDetails.getRole().name() : "";
        if (!"ADMIN".equals(role)) {
            throw new AppException(ErrorCode.INVALID_AUTHORIZATION);
        }
    }
}
