package com.notification.notification_service.service.impl;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.notification.notification_service.dto.request.NotificationMobileRequest;
import com.notification.notification_service.dto.request.RegisterDeviceRequest;
import com.notification.notification_service.entity.UserDevices;
import com.notification.notification_service.repository.UserDeviceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Blackbox test: chỉ kiểm tra hành vi public (input -> side effects/exception),
 * không kiểm tra chi tiết cấu trúc message nội bộ.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MobileNotifyService - Blackbox")
class MobileNotifyServiceBlackboxTest {

    @Mock
    UserDeviceRepository userDeviceRepository;

    @InjectMocks
    MobileNotifyService mobileNotifyService;

    @Test
    @DisplayName("registerDevice: lưu thiết bị khi request hợp lệ")
    void registerDevice_persistsDevice() {
        RegisterDeviceRequest request = new RegisterDeviceRequest("user-1", "token-1");

        assertDoesNotThrow(() -> mobileNotifyService.registerDevice(request));

        verify(userDeviceRepository, times(1)).save(any(UserDevices.class));
    }

    @Test
    @DisplayName("sendMobileNotification: không ném lỗi khi user chưa có thiết bị (skip gửi)")
    void sendMobileNotification_doesNotThrowWhenNoDevices() {
        NotificationMobileRequest request = NotificationMobileRequest.builder()
                .userId("user-no-device")
                .tittle("T")
                .body("B")
                .build();
        when(userDeviceRepository.findAllByUserId("user-no-device")).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> mobileNotifyService.sendMobileNotification(request));
    }

    @Test
    @DisplayName("sendMobileNotification: ném FirebaseMessagingException khi Firebase lỗi")
    void sendMobileNotification_propagatesFirebaseException() throws FirebaseMessagingException {
        NotificationMobileRequest request = NotificationMobileRequest.builder()
                .userId("user-1")
                .tittle("T")
                .body("B")
                .build();
        when(userDeviceRepository.findAllByUserId("user-1")).thenReturn(
                List.of(UserDevices.builder().userId("user-1").deviceToken("token").build())
        );

        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        FirebaseMessagingException firebaseError = mock(FirebaseMessagingException.class);
        when(messaging.sendEachForMulticast(any())).thenThrow(firebaseError);

        try (MockedStatic<FirebaseMessaging> firebaseStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseStatic.when(FirebaseMessaging::getInstance).thenReturn(messaging);

            assertThrows(
                    FirebaseMessagingException.class,
                    () -> mobileNotifyService.sendMobileNotification(request)
            );
        }

        verify(userDeviceRepository).findAllByUserId("user-1");
    }

    @Test
    @DisplayName("sendMobileNotification: có thiết bị thì sẽ gọi Firebase gửi multicast")
    void sendMobileNotification_callsFirebaseWhenDevicesExist() throws FirebaseMessagingException {
        NotificationMobileRequest request = NotificationMobileRequest.builder()
                .userId("recipient")
                .tittle("Hello")
                .body("World")
                .build();
        when(userDeviceRepository.findAllByUserId("recipient")).thenReturn(List.of(
                UserDevices.builder().userId("recipient").deviceToken("token-1").build()
        ));

        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        when(messaging.sendEachForMulticast(any())).thenReturn(mock(com.google.firebase.messaging.BatchResponse.class));

        try (MockedStatic<FirebaseMessaging> firebaseStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseStatic.when(FirebaseMessaging::getInstance).thenReturn(messaging);

            assertDoesNotThrow(() -> mobileNotifyService.sendMobileNotification(request));

            verify(messaging, times(1)).sendEachForMulticast(any());
        }
    }

    @Test
    @DisplayName("sendMobileNotification: không có token thì không gọi Firebase")
    void sendMobileNotification_doesNotCallFirebaseWhenNoToken() throws FirebaseMessagingException {
        NotificationMobileRequest request = NotificationMobileRequest.builder()
                .userId("user-42")
                .tittle("Title")
                .body("Body")
                .build();
        when(userDeviceRepository.findAllByUserId("user-42")).thenReturn(Collections.emptyList());

        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        try (MockedStatic<FirebaseMessaging> firebaseStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseStatic.when(FirebaseMessaging::getInstance).thenReturn(messaging);

            assertDoesNotThrow(() -> mobileNotifyService.sendMobileNotification(request));

            verify(messaging, never()).sendEachForMulticast(any());
        }
    }
}

