package com.actvn.enotary.controller;

import com.actvn.enotary.dto.response.ApiResponse;
import com.actvn.enotary.dto.response.ApiResponseUtil;
import com.actvn.enotary.dto.response.BlockchainNodeResponse;
import com.actvn.enotary.dto.response.BlockchainSummaryResponse;
import com.actvn.enotary.dto.response.BlockchainTransactionResponse;
import com.actvn.enotary.service.BlockchainService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/blockchain")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class BlockchainAdminController {
    private final BlockchainService blockchainService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<BlockchainSummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponseUtil.success(blockchainService.getSummary()));
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<List<BlockchainTransactionResponse>>> getTransactions() {
        return ResponseEntity.ok(ApiResponseUtil.success(blockchainService.getRecentTransactions()));
    }

    @GetMapping("/nodes")
    public ResponseEntity<ApiResponse<List<BlockchainNodeResponse>>> getNodes() {
        return ResponseEntity.ok(ApiResponseUtil.success(blockchainService.getNodes()));
    }
}
