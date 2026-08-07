package com.actvn.enotary.video;

import com.actvn.enotary.exception.AppException;
import com.actvn.enotary.security.JwtUtil;
import com.actvn.enotary.service.VideoSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoSignalingWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;
    private final VideoSessionService videoSessionService;
    private final VideoSignalingService signalingService;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        try {
            VideoSignalMessage payload = objectMapper.readValue(message.getPayload(), VideoSignalMessage.class);

            if (payload.getType() == null) {
                log.warn("[Signaling] Null signal type from session {}", session.getId());
                sendError(session, "Loại tín hiệu không hợp lệ.");
                return;
            }

            log.debug("[Signaling] Received {} from {}", payload.getType(), session.getId());

            switch (payload.getType()) {
                case JOIN -> handleJoin(session, payload);
                case OFFER, ANSWER, ICE -> relay(session, payload);
                case LEAVE -> handleLeave(session);
                case END, SESSION_CONTROL, DOCUMENT_PRESENTATION, CLIENT_CONSENT, SIGNATURE_COMPLETED, EVIDENCE_CAPTURED, RECORDING_STATE -> relay(session, payload);
                default -> {
                    log.warn("[Signaling] Unsupported signal type: {}", payload.getType());
                    sendError(session, "Tín hiệu không được hỗ trợ.");
                }
            }
        } catch (Exception e) {
            log.error("[Signaling] Error handling message", e);
            try {
                sendError(session, "Lỗi xử lý tin nhắn: " + e.getMessage());
            } catch (Exception inner) {
                log.error("[Signaling] Failed to send error message", inner);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws IOException {
        log.info("[Signaling] Connection closed for session {} with status {}", session.getId(), status);
        notifyPeerLeft(session);
    }

    private void handleJoin(WebSocketSession webSocketSession, VideoSignalMessage payload) throws IOException {
        if (payload.getRoomId() == null || payload.getToken() == null || payload.getAuthToken() == null) {
            log.warn("[Signaling] JOIN missing roomId/token/authToken from {}", webSocketSession.getId());
            sendError(webSocketSession, "Thiếu roomId hoặc token xác thực.");
            return;
        }

        try {
            if (!jwtUtil.validateToken(payload.getAuthToken())) {
                log.warn("[Signaling] Invalid auth token from session {}", webSocketSession.getId());
                sendError(webSocketSession, "Token đăng nhập không hợp lệ.");
                webSocketSession.close(CloseStatus.NOT_ACCEPTABLE.withReason("Invalid auth token"));
                return;
            }

            String userEmail = jwtUtil.extractEmail(payload.getAuthToken());
            log.info("[Signaling] JOIN request: roomId={}, email={}", payload.getRoomId(), userEmail);

            videoSessionService.validateParticipantAccess(payload.getRoomId(), payload.getToken(), userEmail);
            VideoSignalingService.JoinResult joinResult = signalingService.join(payload.getRoomId(), userEmail, webSocketSession);
            webSocketSession.getAttributes().put("video-signaling-email", userEmail);
            webSocketSession.getAttributes().put("video-signaling-room", payload.getRoomId());

            log.info("[Signaling] User {} joined room {}, participant count: {}", userEmail, payload.getRoomId(), joinResult.participantCount());

            sendMessage(webSocketSession, VideoSignalMessage.builder()
                    .type(VideoSignalType.JOINED)
                    .roomId(payload.getRoomId())
                    .sender(userEmail)
                    .payload(objectMapper.createObjectNode()
                            .put("participantCount", joinResult.participantCount())
                            .put("offererEmail", joinResult.offererEmail()))
                    .build());

            if (joinResult.participantCount() == 2) {
                log.info("[Signaling] Both participants joined, sending READY signals");
                broadcast(payload.getRoomId(), userEmail, VideoSignalMessage.builder()
                        .type(VideoSignalType.READY)
                        .roomId(payload.getRoomId())
                        .sender(userEmail)
                        .payload(objectMapper.createObjectNode().put("offererEmail", joinResult.offererEmail()))
                        .build());
                sendMessage(webSocketSession, VideoSignalMessage.builder()
                        .type(VideoSignalType.READY)
                        .roomId(payload.getRoomId())
                        .sender(userEmail)
                        .payload(objectMapper.createObjectNode().put("offererEmail", joinResult.offererEmail()))
                        .build());
            }
        } catch (AppException exception) {
            log.warn("[Signaling] Authorization error: {}", exception.getMessage());
            sendError(webSocketSession, exception.getMessage());
            webSocketSession.close(CloseStatus.POLICY_VIOLATION.withReason("Unauthorized participant"));
        } catch (IllegalStateException exception) {
            log.warn("[Signaling] Room full error: {}", exception.getMessage());
            sendError(webSocketSession, exception.getMessage());
            webSocketSession.close(CloseStatus.POLICY_VIOLATION.withReason("Room full"));
        } catch (Exception e) {
            log.error("[Signaling] Unexpected error during JOIN", e);
            sendError(webSocketSession, "Lỗi nội bộ: " + e.getMessage());
        }
    }

    private void relay(WebSocketSession webSocketSession, VideoSignalMessage payload) throws IOException {
        String sender = findSenderEmail(webSocketSession);
        if (sender == null) {
            log.warn("[Signaling] Relay without sender email from {}", webSocketSession.getId());
            sendError(webSocketSession, "Phiên signaling chưa sẵn sàng (chưa JOIN).");
            return;
        }

        if (payload.getRoomId() == null) {
            log.warn("[Signaling] Relay without roomId from {}", webSocketSession.getId());
            sendError(webSocketSession, "Phiên signaling chưa sẵn sàng (thiếu roomId).");
            return;
        }

        try {
            VideoSignalMessage outbound = VideoSignalMessage.builder()
                    .type(payload.getType())
                    .roomId(payload.getRoomId())
                    .sender(sender)
                    .payload(payload.getPayload())
                    .message(payload.getMessage())
                    .build();

            log.debug("[Signaling] Relaying {} from {} in room {}", payload.getType(), sender, payload.getRoomId());
            broadcast(payload.getRoomId(), sender, outbound);
        } catch (Exception e) {
            log.error("[Signaling] Error relaying message", e);
            sendError(webSocketSession, "Lỗi relay tin nhắn: " + e.getMessage());
        }
    }

    private void handleLeave(WebSocketSession webSocketSession) throws IOException {
        log.info("[Signaling] LEAVE from session {}", webSocketSession.getId());
        notifyPeerLeft(webSocketSession);
        webSocketSession.close(CloseStatus.NORMAL);
    }

    private void notifyPeerLeft(WebSocketSession webSocketSession) throws IOException {
        VideoSignalingService.LeaveResult leaveResult = signalingService.leave(webSocketSession);
        if (leaveResult == null) {
            log.debug("[Signaling] No leave result (not in room)");
            return;
        }

        log.info("[Signaling] User {} left room {}", leaveResult.email(), leaveResult.roomId());

        VideoSignalMessage peerLeft = VideoSignalMessage.builder()
                .type(VideoSignalType.PEER_LEFT)
                .roomId(leaveResult.roomId())
                .sender(leaveResult.email())
                .build();

        for (WebSocketSession remainingSession : leaveResult.remainingSessions()) {
            try {
                sendMessage(remainingSession, peerLeft);
            } catch (Exception e) {
                log.warn("[Signaling] Failed to notify peer left", e);
            }
        }
    }

    private void broadcast(String roomId, String senderEmail, VideoSignalMessage message) throws IOException {
        List<WebSocketSession> recipients = signalingService.getOtherParticipants(roomId, senderEmail);
        if (recipients.isEmpty()) {
            log.debug("[Signaling] No other participants to broadcast to in room {}", roomId);
            return;
        }

        for (WebSocketSession recipient : recipients) {
            try {
                sendMessage(recipient, message);
            } catch (Exception e) {
                log.warn("[Signaling] Failed to send message to participant", e);
            }
        }
    }

    private String findSenderEmail(WebSocketSession webSocketSession) {
        Object value = webSocketSession.getAttributes().get("video-signaling-email");
        if (value instanceof String senderEmail) {
            return senderEmail;
        }
        return null;
    }

    private void sendError(WebSocketSession session, String message) throws IOException {
        sendMessage(session, VideoSignalMessage.builder()
                .type(VideoSignalType.ERROR)
                .message(message)
                .build());
    }

    private void sendMessage(WebSocketSession session, VideoSignalMessage payload) throws IOException {
        if (!session.isOpen()) {
            log.debug("[Signaling] Session {} is closed, cannot send message", session.getId());
            return;
        }
        try {
            String jsonMessage = objectMapper.writeValueAsString(payload);
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(jsonMessage));
                }
            }
            log.debug("[Signaling] Sent {} to session {}", payload.getType(), session.getId());
        } catch (IOException e) {
            log.error("[Signaling] Failed to send message to session {}", session.getId(), e);
            throw e;
        }
    }
}

