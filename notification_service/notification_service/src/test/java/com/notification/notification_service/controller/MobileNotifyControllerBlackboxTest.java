package com.notification.notification_service.controller;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.notification.notification_service.dto.ApiResponse;
import com.notification.notification_service.dto.request.NotificationMobileRequest;
import com.notification.notification_service.dto.request.RegisterDeviceRequest;
import com.notification.notification_service.service.impl.MobileNotifyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;


@DisplayName("MobileNotifyController - Blackbox")
class MobileNotifyControllerBlackboxTest {

    @Test
    @DisplayName("POST /register: trả về ApiResponse với message thành công")
    void registerDevices_returnsSuccessResponse() {
        MobileNotifyService mobileNotifyService = mock(MobileNotifyService.class);
        MobileNotifyController controller = new MobileNotifyController(mobileNotifyService);
        doNothing().when(mobileNotifyService).registerDevice(any());

        ApiResponse<Void> resp = controller.registerDevices(new RegisterDeviceRequest("u1", "token-123"));

        verify(mobileNotifyService).registerDevice(any(RegisterDeviceRequest.class));
        assertEquals(1000, resp.getCode());
        assertEquals("Register device thanh cong", resp.getMessage());
    }

    @Test
    @DisplayName("POST /notify: trả về ApiResponse với message thành công")
    void sendMobileNotification_returnsSuccessResponse() throws FirebaseMessagingException {
        MobileNotifyService mobileNotifyService = mock(MobileNotifyService.class);
        MobileNotifyController controller = new MobileNotifyController(mobileNotifyService);
        doNothing().when(mobileNotifyService).sendMobileNotification(any());

        ApiResponse<Void> resp = controller.sendMobileNotification(
                NotificationMobileRequest.builder()
                        .userId("u1")
                        .tittle("Hello")
                        .body("World")
                        .build()
        );

        verify(mobileNotifyService).sendMobileNotification(any(NotificationMobileRequest.class));
        assertEquals(1000, resp.getCode());
        assertEquals("Send notification success", resp.getMessage());
    }

    @Test
    @DisplayName("POST /notify: khi service ném FirebaseMessagingException thì controller cũng ném")
    void sendMobileNotification_whenFirebaseError_throws() throws FirebaseMessagingException {
        MobileNotifyService mobileNotifyService = mock(MobileNotifyService.class);
        MobileNotifyController controller = new MobileNotifyController(mobileNotifyService);
        FirebaseMessagingException ex = Mockito.mock(FirebaseMessagingException.class);
        doThrow(ex).when(mobileNotifyService).sendMobileNotification(any());

        assertThrows(
                FirebaseMessagingException.class,
                () -> controller.sendMobileNotification(
                        NotificationMobileRequest.builder()
                                .userId("u1")
                                .tittle("Hello")
                                .body("World")
                                .build()
                )
        );
    }
}

