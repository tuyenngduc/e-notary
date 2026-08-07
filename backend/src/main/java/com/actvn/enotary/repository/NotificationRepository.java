package com.actvn.enotary.repository;

import com.actvn.enotary.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByUser_UserIdOrderByCreatedAtDesc(UUID userId);

    long countByUser_UserIdAndReadFalse(UUID userId);

    Optional<Notification> findByNotificationIdAndUser_UserId(UUID notificationId, UUID userId);
}
