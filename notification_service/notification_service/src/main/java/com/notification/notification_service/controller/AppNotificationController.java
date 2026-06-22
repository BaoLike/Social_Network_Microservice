package com.notification.notification_service.controller;

import com.notification.notification_service.dto.ApiResponse;
import com.notification.notification_service.dto.request.CreateInteractionNotificationRequest;
import com.notification.notification_service.dto.response.AppNotificationResponse;
import com.notification.notification_service.service.IAppNotificationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class AppNotificationController {

    IAppNotificationService appNotificationService;

    @PostMapping("/internal/interaction")
    ApiResponse<Void> createInteractionNotification(@RequestBody CreateInteractionNotificationRequest request) {
        appNotificationService.createInteractionNotification(request);
        return ApiResponse.<Void>builder()
                .message("Notification created")
                .build();
    }

    @GetMapping("/list")
    ApiResponse<List<AppNotificationResponse>> getNotifications(@RequestParam("userId") String userId) {
        return ApiResponse.<List<AppNotificationResponse>>builder()
                .result(appNotificationService.getNotificationsForUser(userId))
                .build();
    }
}
