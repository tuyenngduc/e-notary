package com.actvn.enotary.dto.response;

import com.actvn.enotary.entity.Notification;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class NotificationResponse {
    private UUID id;
    private String title;
    private String message;
    private String type;
    private UUID requestId;
    private UUID appointmentId;
    private boolean isRead;
    private OffsetDateTime createdAt;
    private OffsetDateTime readAt;

    public static NotificationResponse fromEntity(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getNotificationId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType())
                .requestId(notification.getRequest() == null ? null : notification.getRequest().getRequestId())
                .appointmentId(notification.getAppointment() == null ? null : notification.getAppointment().getAppointmentId())
                .isRead(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .readAt(notification.getReadAt())
                .build();
    }
}
