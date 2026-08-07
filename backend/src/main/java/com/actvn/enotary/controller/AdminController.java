package com.actvn.enotary.controller;

import com.actvn.enotary.dto.request.NotaryOfficeRequest;
import com.actvn.enotary.dto.request.NotaryServiceTypeRequest;
import com.actvn.enotary.dto.request.DocumentRequirementConfigRequest;
import com.actvn.enotary.dto.request.DocumentTypeRequest;
import com.actvn.enotary.dto.request.SignUpRequest;
import com.actvn.enotary.dto.response.AdminActionLogResponse;
import com.actvn.enotary.dto.response.ApiResponse;
import com.actvn.enotary.dto.response.ApiResponseUtil;
import com.actvn.enotary.dto.response.NotaryOfficeResponse;
import com.actvn.enotary.dto.response.NotaryServiceTypeResponse;
import com.actvn.enotary.dto.response.DocumentRequirementConfigResponse;
import com.actvn.enotary.dto.response.DocumentTypeResponse;
import com.actvn.enotary.dto.response.UserResponse;
import com.actvn.enotary.entity.AuditLog;
import com.actvn.enotary.entity.NotaryOffice;
import com.actvn.enotary.entity.NotaryServiceType;
import com.actvn.enotary.enums.Role;
import com.actvn.enotary.enums.VerificationStatus;
import com.actvn.enotary.exception.AppException;
import com.actvn.enotary.exception.ErrorCode;
import com.actvn.enotary.security.CustomUserDetails;
import com.actvn.enotary.service.AuditLogService;
import com.actvn.enotary.service.NotaryOfficeService;
import com.actvn.enotary.service.NotaryServiceTypeService;
import com.actvn.enotary.service.DocumentRequirementConfigService;
import com.actvn.enotary.service.DocumentTypeService;
import com.actvn.enotary.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final NotaryServiceTypeService notaryServiceTypeService;
    private final DocumentRequirementConfigService documentRequirementConfigService;
    private final DocumentTypeService documentTypeService;
    private final NotaryOfficeService notaryOfficeService;

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getAllUsers(
            Authentication authentication,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) VerificationStatus verificationStatus,
            @PageableDefault(size = 10, sort = "email") Pageable pageable) {
        ensureAdmin(authentication);
        return ResponseEntity.ok(
                ApiResponseUtil.success(
                        userService.getUsers(role, verificationStatus, pageable),
                        "Lấy danh sách người dùng thành công"
                )
        );
    }

    @PostMapping("/notaries")
    public ResponseEntity<ApiResponse<UserResponse>> createNotary(
            Authentication authentication,
            @Valid @RequestBody SignUpRequest request) {

        CustomUserDetails admin = ensureAdmin(authentication);

        var created = userService.createNotary(request, admin.getId());
        URI location = URI.create("/api/users/" + created.getUserId());
        return ResponseEntity.created(location).body(
                ApiResponseUtil.created(UserResponse.fromUser(created), "Tạo công chứng viên thành công")
        );
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> deleteUser(
            Authentication authentication,
            @PathVariable UUID userId) {
        CustomUserDetails admin = ensureAdmin(authentication);
        UserResponse deleted = UserResponse.fromUser(userService.deleteUserByAdmin(userId, admin.getId()));
        return ResponseEntity.ok(ApiResponseUtil.success(deleted, "Xóa tài khoản thành công"));
    }

    @PutMapping("/users/{userId}/status")
    public ResponseEntity<ApiResponse<UserResponse>> toggleUserStatus(
            Authentication authentication,
            @PathVariable UUID userId) {
        CustomUserDetails admin = ensureAdmin(authentication);
        UserResponse updated = UserResponse.fromUser(userService.toggleUserStatus(userId, admin.getId()));
        String message = updated.getIsActive() ? "Đã mở khóa tài khoản" : "Đã khóa tài khoản";
        return ResponseEntity.ok(ApiResponseUtil.success(updated, message));
    }

    @GetMapping("/notary-access-history")
    public ResponseEntity<ApiResponse<List<AdminActionLogResponse>>> getActionHistory(
            Authentication authentication,
            @RequestParam(defaultValue = "20") int limit) {
        ensureAdmin(authentication);
        List<AuditLog> logs = auditLogService.getActionHistory(limit);
        List<AdminActionLogResponse> responses = logs.stream()
                .map(AdminActionLogResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(
                ApiResponseUtil.success(responses, "Lấy lịch sử thao tác thành công")
        );
    }

    @GetMapping("/services")
    public ResponseEntity<ApiResponse<Page<NotaryServiceTypeResponse>>> getAllServices(
            Authentication authentication,
            @PageableDefault(size = 20, sort = "serviceCode") Pageable pageable) {
        ensureAdmin(authentication);
        Page<NotaryServiceTypeResponse> responses = notaryServiceTypeService.getAll(pageable)
                .map(NotaryServiceTypeResponse::fromEntity);
        return ResponseEntity.ok(ApiResponseUtil.success(responses, "Lấy danh sách dịch vụ thành công"));
    }

    @PostMapping("/services")
    public ResponseEntity<ApiResponse<NotaryServiceTypeResponse>> createService(
            Authentication authentication,
            @Valid @RequestBody NotaryServiceTypeRequest request) {
        ensureAdmin(authentication);
        
        NotaryServiceType entity = new NotaryServiceType();
        entity.setServiceCode(request.getServiceCode());
        entity.setName(request.getName());
        entity.setBasePrice(request.getBasePrice());
        entity.setDescription(request.getDescription());
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }
        if (request.getRequiresTemplate() != null) {
            entity.setRequiresTemplate(request.getRequiresTemplate());
        }
        
        NotaryServiceType created = notaryServiceTypeService.create(entity);
        return ResponseEntity.ok(ApiResponseUtil.success(NotaryServiceTypeResponse.fromEntity(created), "Tạo dịch vụ thành công"));
    }

    @PutMapping("/services/{id}")
    public ResponseEntity<ApiResponse<NotaryServiceTypeResponse>> updateService(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody NotaryServiceTypeRequest request) {
        ensureAdmin(authentication);
        
        NotaryServiceType entity = new NotaryServiceType();
        entity.setServiceCode(request.getServiceCode());
        entity.setName(request.getName());
        entity.setBasePrice(request.getBasePrice());
        entity.setDescription(request.getDescription());
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }
        if (request.getRequiresTemplate() != null) {
            entity.setRequiresTemplate(request.getRequiresTemplate());
        }
        
        NotaryServiceType updated = notaryServiceTypeService.update(id, entity);
        return ResponseEntity.ok(ApiResponseUtil.success(NotaryServiceTypeResponse.fromEntity(updated), "Cập nhật dịch vụ thành công"));
    }

    @DeleteMapping("/services/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteService(
            Authentication authentication,
            @PathVariable UUID id) {
        ensureAdmin(authentication);
        notaryServiceTypeService.delete(id);
        return ResponseEntity.ok(ApiResponseUtil.success(null, "Xóa dịch vụ thành công"));
    }

    @GetMapping("/document-requirements")
    public ResponseEntity<ApiResponse<List<DocumentRequirementConfigResponse>>> getDocumentRequirementConfigs(
            Authentication authentication) {
        ensureAdmin(authentication);
        return ResponseEntity.ok(ApiResponseUtil.success(
                documentRequirementConfigService.listAll(),
                "Lấy cấu hình hồ sơ bắt buộc thành công"
        ));
    }

    @PostMapping("/document-requirements")
    public ResponseEntity<ApiResponse<DocumentRequirementConfigResponse>> createDocumentRequirementConfig(
            Authentication authentication,
            @Valid @RequestBody DocumentRequirementConfigRequest request) {
        ensureAdmin(authentication);
        DocumentRequirementConfigResponse response = documentRequirementConfigService.create(request);
        return ResponseEntity.ok(ApiResponseUtil.success(response, "Tạo cấu hình hồ sơ thành công"));
    }

    @PutMapping("/document-requirements/{serviceId}")
    public ResponseEntity<ApiResponse<DocumentRequirementConfigResponse>> updateDocumentRequirementConfig(
            Authentication authentication,
            @PathVariable UUID serviceId,
            @Valid @RequestBody DocumentRequirementConfigRequest request) {
        ensureAdmin(authentication);
        DocumentRequirementConfigResponse response = documentRequirementConfigService.update(serviceId, request);
        return ResponseEntity.ok(ApiResponseUtil.success(response, "Cập nhật cấu hình hồ sơ thành công"));
    }

    @DeleteMapping("/document-requirements/{serviceId}")
    public ResponseEntity<ApiResponse<Void>> deleteDocumentRequirementConfig(
            Authentication authentication,
            @PathVariable UUID serviceId) {
        ensureAdmin(authentication);
        documentRequirementConfigService.delete(serviceId);
        return ResponseEntity.ok(ApiResponseUtil.success(null, "Vô hiệu hóa dịch vụ thành công"));
    }

    @GetMapping("/document-types")
    public ResponseEntity<ApiResponse<List<DocumentTypeResponse>>> getDocumentTypes(Authentication authentication) {
        ensureAdmin(authentication);
        List<DocumentTypeResponse> responses = documentTypeService.listAll().stream()
                .map(DocumentTypeResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(ApiResponseUtil.success(responses, "Lấy danh mục hồ sơ thành công"));
    }

    @PostMapping("/document-types")
    public ResponseEntity<ApiResponse<DocumentTypeResponse>> createDocumentType(
            Authentication authentication,
            @Valid @RequestBody DocumentTypeRequest request) {
        ensureAdmin(authentication);
        return ResponseEntity.ok(ApiResponseUtil.success(
                DocumentTypeResponse.fromEntity(documentTypeService.create(request)),
                "Tạo loại giấy tờ thành công"
        ));
    }

    @PutMapping("/document-types/{code}")
    public ResponseEntity<ApiResponse<DocumentTypeResponse>> updateDocumentType(
            Authentication authentication,
            @PathVariable String code,
            @Valid @RequestBody DocumentTypeRequest request) {
        ensureAdmin(authentication);
        return ResponseEntity.ok(ApiResponseUtil.success(
                DocumentTypeResponse.fromEntity(documentTypeService.update(code, request)),
                "Cập nhật loại hồ sơ thành công"
        ));
    }

    @GetMapping("/offices")
    public ResponseEntity<ApiResponse<Page<NotaryOfficeResponse>>> getAllOffices(
            Authentication authentication,
            @PageableDefault(size = 100, sort = "name") Pageable pageable) {
        ensureAdmin(authentication);
        Page<NotaryOfficeResponse> responses = notaryOfficeService.getAll(pageable)
                .map(NotaryOfficeResponse::fromEntity);
        return ResponseEntity.ok(ApiResponseUtil.success(responses, "Lấy danh sách văn phòng công chứng thành công"));
    }

    @PostMapping("/offices")
    public ResponseEntity<ApiResponse<NotaryOfficeResponse>> createOffice(
            Authentication authentication,
            @Valid @RequestBody NotaryOfficeRequest request) {
        ensureAdmin(authentication);

        NotaryOffice created = notaryOfficeService.create(request);
        return ResponseEntity.ok(ApiResponseUtil.success(
                NotaryOfficeResponse.fromEntity(created),
                "Tạo văn phòng công chứng thành công"
        ));
    }

    @PutMapping("/offices/{id}")
    public ResponseEntity<ApiResponse<NotaryOfficeResponse>> updateOffice(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody NotaryOfficeRequest request) {
        ensureAdmin(authentication);

        NotaryOffice updated = notaryOfficeService.update(id, request);
        return ResponseEntity.ok(ApiResponseUtil.success(
                NotaryOfficeResponse.fromEntity(updated),
                "Cập nhật văn phòng công chứng thành công"
        ));
    }

    @DeleteMapping("/offices/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteOffice(
            Authentication authentication,
            @PathVariable UUID id) {
        ensureAdmin(authentication);
        notaryOfficeService.delete(id);
        return ResponseEntity.ok(ApiResponseUtil.success(null, "Đã ngừng áp dụng văn phòng công chứng"));
    }

    private CustomUserDetails ensureAdmin(Authentication authentication) {

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new AppException(ErrorCode.INVALID_AUTHENTICATION);
        }

        String role = userDetails.getRole() != null ? userDetails.getRole().name() : "";
        if (!"ADMIN".equals(role)) {
            throw new AppException(ErrorCode.INVALID_AUTHORIZATION);
        }

        return userDetails;
    }
}

