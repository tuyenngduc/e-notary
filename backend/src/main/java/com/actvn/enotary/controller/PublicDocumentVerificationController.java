package com.actvn.enotary.controller;

import com.actvn.enotary.dto.response.ApiResponse;
import com.actvn.enotary.dto.response.ApiResponseUtil;
import com.actvn.enotary.dto.response.PublicDocumentVerificationResponse;
import com.actvn.enotary.service.BlockchainService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/public/documents")
@RequiredArgsConstructor
public class PublicDocumentVerificationController {
    private final BlockchainService blockchainService;

    @PostMapping(value = "/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PublicDocumentVerificationResponse>> verifyDocument(
            @RequestParam("file") MultipartFile file
    ) {
        PublicDocumentVerificationResponse response = blockchainService.verifyPublicDocument(file);
        String message = response.isVerified()
                ? "Xác minh tài liệu thành công"
                : "Không tìm thấy tài liệu trong hệ thống";
        return ResponseEntity.ok(ApiResponseUtil.success(response, message));
    }
}
