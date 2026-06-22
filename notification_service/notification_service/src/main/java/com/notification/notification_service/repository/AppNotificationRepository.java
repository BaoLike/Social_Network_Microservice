package com.notification.notification_service.repository;

import com.notification.notification_service.entity.AppNotification;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppNotificationRepository extends MongoRepository<AppNotification, String> {
    List<AppNotification> findByRecipientUserIdOrderByCreatedAtDesc(String recipientUserId);
}
