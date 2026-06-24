package com.notification.notification_service.service.impl;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MulticastMessage;
import com.notification.notification_service.dto.request.NotificationMobileRequest;
import com.notification.notification_service.dto.request.RegisterDeviceRequest;
import com.notification.notification_service.entity.UserDevices;
import com.notification.notification_service.repository.UserDeviceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Whitebox test: kiểm tra logic nội bộ (mapping entity, distinct token, build message).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MobileNotifyService - Whitebox")
class MobileNotifyServiceWhiteboxTest {

    @Mock
    UserDeviceRepository userDeviceRepository;

    @InjectMocks
    MobileNotifyService mobileNotifyService;

    @Test
    @DisplayName("registerDevice: map đúng userId/tokenDevice vào entity trước khi save")
    void registerDevice_mapsFieldsToEntity() {
        RegisterDeviceRequest request = new RegisterDeviceRequest("user-1", "device-token-abc");

        mobileNotifyService.registerDevice(request);

        verify(userDeviceRepository).deleteByDeviceToken("device-token-abc");
        ArgumentCaptor<UserDevices> captor = ArgumentCaptor.forClass(UserDevices.class);
        verify(userDeviceRepository).save(captor.capture());
        UserDevices saved = captor.getValue();
        assertEquals("user-1", saved.getUserId());
        assertEquals("device-token-abc", saved.getDeviceToken());
        verifyNoMoreInteractions(userDeviceRepository);
    }

    @Test
    @DisplayName("sendMobileNotification: distinct token trước khi gửi")
    void sendMobileNotification_deduplicatesTokens() throws FirebaseMessagingException, ReflectiveOperationException {
        NotificationMobileRequest request = NotificationMobileRequest.builder()
                .userId("user-1")
                .tittle("Hello")
                .body("World")
                .build();

        when(userDeviceRepository.findAllByUserId("user-1")).thenReturn(List.of(
                UserDevices.builder().userId("user-1").deviceToken("token-a").build(),
                UserDevices.builder().userId("user-1").deviceToken("token-a").build(),
                UserDevices.builder().userId("user-1").deviceToken("token-b").build()
        ));

        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        when(messaging.sendEachForMulticast(any(MulticastMessage.class)))
                .thenReturn(mock(com.google.firebase.messaging.BatchResponse.class));

        try (MockedStatic<FirebaseMessaging> firebaseStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseStatic.when(FirebaseMessaging::getInstance).thenReturn(messaging);

            mobileNotifyService.sendMobileNotification(request);

            ArgumentCaptor<MulticastMessage> msgCaptor = ArgumentCaptor.forClass(MulticastMessage.class);
            verify(messaging, times(1)).sendEachForMulticast(msgCaptor.capture());

            MulticastMessage msg = msgCaptor.getValue();
            List<String> tokens = extractTokens(msg);
            assertEquals(2, tokens.size());
            assertTrue(tokens.containsAll(List.of("token-a", "token-b")));
        }
    }

    @Test
    @DisplayName("sendMobileNotification: set đúng title/body vào notification")
    void sendMobileNotification_setsTitleAndBody() throws FirebaseMessagingException, ReflectiveOperationException {
        NotificationMobileRequest request = NotificationMobileRequest.builder()
                .userId("user-1")
                .tittle("Push title")
                .body("Push body")
                .build();

        when(userDeviceRepository.findAllByUserId("user-1")).thenReturn(List.of(
                UserDevices.builder().userId("user-1").deviceToken("t1").build()
        ));

        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        when(messaging.sendEachForMulticast(any(MulticastMessage.class)))
                .thenReturn(mock(com.google.firebase.messaging.BatchResponse.class));

        try (MockedStatic<FirebaseMessaging> firebaseStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseStatic.when(FirebaseMessaging::getInstance).thenReturn(messaging);

            mobileNotifyService.sendMobileNotification(request);

            ArgumentCaptor<MulticastMessage> msgCaptor = ArgumentCaptor.forClass(MulticastMessage.class);
            verify(messaging).sendEachForMulticast(msgCaptor.capture());

            Object notification = extractField(msgCaptor.getValue(), "notification");
            assertNotNull(notification);
            // firebase-admin 9.x Notification doesn't always expose getters; read private fields.
            String title = (String) extractField(notification, "title");
            String body = (String) extractField(notification, "body");
            assertEquals("Push title", title);
            assertEquals("Push body", body);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractTokens(MulticastMessage message) throws ReflectiveOperationException {
        Object raw = extractField(message, "tokens");
        return (List<String>) raw;
    }

    private static Object extractField(Object target, String fieldName) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}

