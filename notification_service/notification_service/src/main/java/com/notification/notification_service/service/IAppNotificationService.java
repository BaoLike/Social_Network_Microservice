package com.notification.notification_service.service;

import com.notification.notification_service.dto.request.CreateInteractionNotificationRequest;
import com.notification.notification_service.dto.response.AppNotificationResponse;

import java.util.List;

public interface IAppNotificationService {
    void createInteractionNotification(CreateInteractionNotificationRequest request);

    List<AppNotificationResponse> getNotificationsForUser(String userId);
}
