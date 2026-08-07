package com.actvn.enotary.controller;
import com.actvn.enotary.dto.request.CreateVideoSessionRequest;
import com.actvn.enotary.dto.request.EvidenceCaptureRequest;
import com.actvn.enotary.dto.request.SignVideoDocumentRequest;
import com.actvn.enotary.dto.response.ApiResponse;
import com.actvn.enotary.dto.response.ApiResponseUtil;
import com.actvn.enotary.dto.response.DocumentResponse;
import com.actvn.enotary.dto.response.SignVideoDocumentResponse;
import com.actvn.enotary.dto.response.VideoSessionResponse;
import com.actvn.enotary.entity.Document;
import com.actvn.enotary.exception.AppException;
import com.actvn.enotary.exception.ErrorCode;
import com.actvn.enotary.security.CustomUserDetails;
import com.actvn.enotary.service.VideoSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;
@RestController
@RequestMapping("/api/video")
@RequiredArgsConstructor
public class VideoSessionController {
    private final VideoSessionService videoSessionService;

    @Value("${app.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;
    /**
     * Tạo video session cho appointment (Notary/Admin gọi API này)
     * POST /api/video/sessions
     */
    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<VideoSessionResponse>> createVideoSession(
            Authentication authentication,
            @Valid @RequestBody CreateVideoSessionRequest request) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new AppException(ErrorCode.INVALID_AUTHENTICATION);
        }
        String role = userDetails.getRole() != null ? userDetails.getRole().name() : "";
        boolean isNotary = "NOTARY".equals(role);
        boolean isAdmin = "ADMIN".equals(role);
        if (!isNotary && !isAdmin) {
            throw new AppException(ErrorCode.INVALID_AUTHORIZATION);
        }
        VideoSessionResponse response = videoSessionService.createVideoSession(request);
        URI location = URI.create("/api/video/sessions/" + response.getSessionId());
        return ResponseEntity.created(location).body(
                ApiResponseUtil.created(response, "Tạo phiên video thành công")
        );
    }
    /**
     * Lấy thông tin video session bằng ID
     * GET /api/video/sessions/{id}
     */
    @GetMapping("/sessions/{id}")
    public ResponseEntity<ApiResponse<VideoSessionResponse>> getVideoSession(
            Authentication authentication,
            @PathVariable("id") UUID sessionId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new AppException(ErrorCode.INVALID_AUTHENTICATION);
        }
        VideoSessionResponse response = videoSessionService.getVideoSession(sessionId);
        return ResponseEntity.ok(ApiResponseUtil.success(response));
    }
    /**
     * Lấy video session bằng appointment ID
     * GET /api/video/appointments/{appointmentId}/session
     */
    @GetMapping("/appointments/{appointmentId}/session")
    public ResponseEntity<ApiResponse<VideoSessionResponse>> getVideoSessionByAppointment(
            Authentication authentication,
            @PathVariable("appointmentId") UUID appointmentId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new AppException(ErrorCode.INVALID_AUTHENTICATION);
        }
        VideoSessionResponse response = videoSessionService.getVideoSessionByAppointmentId(appointmentId);
        return ResponseEntity.ok(ApiResponseUtil.success(response));
    }
    /**
     * Verify session token (dùng khi client truy cập từ email link)
     * GET /api/video/verify-token?token={token}
     */
    @GetMapping("/verify-token")
    public ResponseEntity<ApiResponse<VideoSessionResponse>> verifyToken(
            @RequestParam("token") String token) {
        VideoSessionResponse response = videoSessionService.verifySessionToken(token);
        return ResponseEntity.ok(ApiResponseUtil.success(response));
    }
    /**
     * Join vào video room
     * POST /api/video/room/{roomId}/join
     */
    @PostMapping("/room/{roomId}/join")
    public ResponseEntity<ApiResponse<VideoSessionResponse>> joinSession(
            Authentication authentication,
            @PathVariable("roomId") String roomId,
            @RequestParam("token") String token) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new AppException(ErrorCode.INVALID_AUTHENTICATION);
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        VideoSessionResponse response = videoSessionService.joinSession(roomId, userDetails.getUsername(), token);
        return ResponseEntity.ok(ApiResponseUtil.success(response, "Tham gia phòng thành công"));
    }
    /**
     * Kết thúc video session
     * POST /api/video/sessions/{id}/end
     */
    @PostMapping("/sessions/{id}/end")
    public ResponseEntity<ApiResponse<VideoSessionResponse>> endSession(
            Authentication authentication,
            @PathVariable("id") UUID sessionId,
            @RequestParam(value = "reason", defaultValue = "Normal end") String reason) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new AppException(ErrorCode.INVALID_AUTHENTICATION);
        }
        VideoSessionResponse response = videoSessionService.endSession(sessionId, reason);
        return ResponseEntity.ok(ApiResponseUtil.success(response, "Kết thúc phiên video thành công"));
    }

    @PostMapping("/sessions/{id}/evidence")
    public ResponseEntity<ApiResponse<DocumentResponse>> saveEvidencePhoto(
            Authentication authentication,
            @PathVariable("id") UUID sessionId,
            @Valid @RequestBody EvidenceCaptureRequest request) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new AppException(ErrorCode.INVALID_AUTHENTICATION);
        }
        String role = userDetails.getRole() != null ? userDetails.getRole().name() : "";
        if (!"NOTARY".equals(role)) {
            throw new AppException(ErrorCode.INVALID_AUTHORIZATION);
        }

        Document document = videoSessionService.saveEvidencePhoto(sessionId, userDetails.getUsername(), request.getImageData());
        return ResponseEntity.ok(ApiResponseUtil.success(
                DocumentResponse.fromEntity(document),
                "Lưu ảnh bằng chứng thành công"
        ));
    }

    @PostMapping("/sessions/{id}/signatures")
    public ResponseEntity<ApiResponse<SignVideoDocumentResponse>> signDocument(
            Authentication authentication,
            @PathVariable("id") UUID sessionId,
            @Valid @RequestBody SignVideoDocumentRequest request) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new AppException(ErrorCode.INVALID_AUTHENTICATION);
        }

        SignVideoDocumentResponse response = videoSessionService.signDocument(
                sessionId,
                userDetails.getUsername(),
                request.getDocumentId(),
                request.getSignatureValue(),
                request.getPageNumber(),
                request.getXPercent(),
                request.getYPercent(),
                request.getWidthPercent(),
                request.getHeightPercent()
        );
        return ResponseEntity.ok(ApiResponseUtil.success(response, "Ký số văn bản thành công"));
    }

    /**
     * Hủy video session
     * POST /api/video/sessions/{id}/cancel
     */
    @PostMapping("/sessions/{id}/cancel")
    public ResponseEntity<ApiResponse<VideoSessionResponse>> cancelSession(
            Authentication authentication,
            @PathVariable("id") UUID sessionId,
            @RequestParam(value = "reason", defaultValue = "Cancelled") String reason) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new AppException(ErrorCode.INVALID_AUTHENTICATION);
        }
        String role = userDetails.getRole() != null ? userDetails.getRole().name() : "";
        boolean isNotary = "NOTARY".equals(role);
        boolean isAdmin = "ADMIN".equals(role);
        if (!isNotary && !isAdmin) {
            throw new AppException(ErrorCode.INVALID_AUTHORIZATION);
        }
        VideoSessionResponse response = videoSessionService.cancelSession(sessionId, reason);
        return ResponseEntity.ok(ApiResponseUtil.success(response, "Hủy phiên video thành công"));
    }
    /**
     * Redirect meeting link sang frontend room để render UI video call.
     * GET /api/video/room/{roomId}
     */
    @GetMapping("/room/{roomId}")
    public ResponseEntity<Void> getVideoRoom(
            @PathVariable("roomId") String roomId,
            @RequestParam(value = "token", required = false) String token) {
        if (token != null && !token.isBlank()) {
            videoSessionService.verifySessionToken(token);
        }
        StringBuilder redirectUrl = new StringBuilder(frontendBaseUrl)
                .append("/video/room/")
                .append(roomId);

        if (token != null && !token.isBlank()) {
            redirectUrl.append("?token=").append(token);
        }

        return ResponseEntity.status(302)
                .location(URI.create(redirectUrl.toString()))
                .build();
    }
}
