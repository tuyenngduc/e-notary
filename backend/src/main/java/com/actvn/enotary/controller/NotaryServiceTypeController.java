package com.actvn.enotary.controller;

import com.actvn.enotary.dto.response.ApiResponse;
import com.actvn.enotary.dto.response.ApiResponseUtil;
import com.actvn.enotary.dto.response.NotaryServiceTypeResponse;
import com.actvn.enotary.service.NotaryServiceTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class NotaryServiceTypeController {
    private final NotaryServiceTypeService notaryServiceTypeService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotaryServiceTypeResponse>>> getActiveServices(
            @PageableDefault(size = 100, sort = "serviceCode") Pageable pageable) {
        Page<NotaryServiceTypeResponse> responses = notaryServiceTypeService.getActive(pageable)
                .map(NotaryServiceTypeResponse::fromEntity);
        return ResponseEntity.ok(ApiResponseUtil.success(responses, "Lay danh sach dich vu dang ap dung thanh cong"));
    }
}
