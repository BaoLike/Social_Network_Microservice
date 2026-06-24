package com.call.call_service.repository.httpclient;

import com.call.call_service.dto.request.NotificationMobileRequest;
import com.call.call_service.dto.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "notification-client", url = "${app.service.notification}")
public interface NotificationClient {
    @PostMapping("/notify")
    ApiResponse<Void> sendMobileNotification(@RequestBody NotificationMobileRequest request);
}
