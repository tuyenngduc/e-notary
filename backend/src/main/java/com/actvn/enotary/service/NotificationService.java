package com.actvn.enotary.service;

import com.actvn.enotary.dto.response.NotificationResponse;
import com.actvn.enotary.entity.Appointment;
import com.actvn.enotary.entity.NotaryRequest;
import com.actvn.enotary.entity.Notification;
import com.actvn.enotary.enums.ServiceType;
import com.actvn.enotary.exception.AppException;
import com.actvn.enotary.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private static final String APPOINTMENT_SCHEDULED = "APPOINTMENT_SCHEDULED";
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter APPOINTMENT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy");

    private final NotificationRepository notificationRepository;

    @Transactional
    public void createAppointmentScheduledNotification(NotaryRequest request, Appointment appointment) {
        if (request.getClient() == null) {
            return;
        }

        Notification notification = new Notification();
        notification.setUser(request.getClient());
        notification.setRequest(request);
        notification.setAppointment(appointment);
        notification.setType(APPOINTMENT_SCHEDULED);
        notification.setTitle("Lịch hẹn công chứng đã được xác nhận");
        notification.setMessage(buildAppointmentMessage(request, appointment));

        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listForUser(UUID userId) {
        return notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return notificationRepository.countByUser_UserIdAndReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markAsRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findByNotificationIdAndUser_UserId(notificationId, userId)
                .orElseThrow(() -> new AppException("Không tìm thấy thông báo", HttpStatus.NOT_FOUND));

        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(java.time.OffsetDateTime.now());
        }

        return NotificationResponse.fromEntity(notification);
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        List<Notification> notifications = notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(userId);
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        notifications.stream()
                .filter(notification -> !notification.isRead())
                .forEach(notification -> {
                    notification.setRead(true);
                    notification.setReadAt(now);
                });
    }

    private String buildAppointmentMessage(NotaryRequest request, Appointment appointment) {
        String time = appointment.getScheduledTime() == null
                ? "thời gian đã hẹn"
                : appointment.getScheduledTime()
                        .atZoneSameInstant(VIETNAM_ZONE)
                        .format(APPOINTMENT_TIME_FORMATTER);
        String requestCode = request.getRequestId() == null
                ? ""
                : request.getRequestId().toString().substring(0, 8).toUpperCase();

        if (request.getServiceType() == ServiceType.OFFLINE) {
            String address = appointment.getPhysicalAddress() == null || appointment.getPhysicalAddress().isBlank()
                    ? "văn phòng công chứng"
                    : appointment.getPhysicalAddress();
            return "Hồ sơ " + requestCode + " đã được xác nhận lịch hẹn trực tiếp vào " + time
                    + ". Địa điểm: " + address + ".";
        }

        return "Hồ sơ " + requestCode + " đã được xác nhận lịch hẹn trực tuyến vào " + time
                + ". Vui lòng tham gia đúng giờ trên hệ thống.";
    }
}
