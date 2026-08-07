package com.actvn.enotary.controller;

import com.actvn.enotary.dto.response.ApiResponse;
import com.actvn.enotary.dto.response.ApiResponseUtil;
import com.actvn.enotary.dto.response.PaymentResponse;
import com.actvn.enotary.exception.AppException;
import com.actvn.enotary.exception.ErrorCode;
import com.actvn.enotary.security.CustomUserDetails;
import com.actvn.enotary.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @GetMapping("/request/{requestId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getByRequest(
            Authentication authentication,
            @PathVariable UUID requestId) {
        CustomUserDetails userDetails = requireClient(authentication);
        return ResponseEntity.ok(ApiResponseUtil.success(
                paymentService.getByRequestForClient(requestId, userDetails.getUsername())
        ));
    }

    @PostMapping("/{paymentId}/confirm-transfer")
    public ResponseEntity<ApiResponse<PaymentResponse>> confirmTransfer(
            Authentication authentication,
            @PathVariable UUID paymentId) {
        CustomUserDetails userDetails = requireClient(authentication);
        return ResponseEntity.ok(ApiResponseUtil.success(
                paymentService.confirmBankTransfer(paymentId, userDetails.getUsername()),
                "Xác nhận thanh toán thành công"
        ));
    }

    private CustomUserDetails requireClient(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new AppException(ErrorCode.INVALID_AUTHENTICATION);
        }
        if (userDetails.getRole() == null || !"CLIENT".equals(userDetails.getRole().name())) {
            throw new AppException(ErrorCode.INVALID_AUTHORIZATION);
        }
        return userDetails;
    }
}
