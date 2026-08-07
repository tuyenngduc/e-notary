package com.actvn.enotary.controller;

import com.actvn.enotary.dto.response.ApiResponse;
import com.actvn.enotary.dto.response.ApiResponseUtil;
import com.actvn.enotary.dto.response.NotaryOfficeResponse;
import com.actvn.enotary.service.NotaryOfficeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notary-offices")
@RequiredArgsConstructor
public class NotaryOfficeController {
    private final NotaryOfficeService notaryOfficeService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotaryOfficeResponse>>> getActiveOffices(
            @PageableDefault(size = 100, sort = "name") Pageable pageable) {
        Page<NotaryOfficeResponse> responses = notaryOfficeService.getActive(pageable)
                .map(NotaryOfficeResponse::fromEntity);
        return ResponseEntity.ok(ApiResponseUtil.success(responses, "Lấy danh sách văn phòng công chứng thành công"));
    }
}
