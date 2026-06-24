package com.call.call_service.controller;

import com.call.call_service.dto.request.InitiateCallRequest;
import com.call.call_service.dto.response.ApiResponse;
import com.call.call_service.dto.response.CallSessionResponse;
import com.call.call_service.dto.response.IceServerResponse;
import com.call.call_service.service.impl.CallSessionService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CallController {

    CallSessionService callSessionService;

    @PostMapping("/initiate")
    public ApiResponse<CallSessionResponse> initiate(@Valid @RequestBody InitiateCallRequest request) {
        String callerId = currentUserId();
        return ApiResponse.<CallSessionResponse>builder()
                .result(callSessionService.initiate(callerId, request))
                .build();
    }

    @PostMapping("/{callId}/accept")
    public ApiResponse<CallSessionResponse> accept(@PathVariable String callId) {
        return ApiResponse.<CallSessionResponse>builder()
                .result(callSessionService.accept(callId, currentUserId()))
                .build();
    }

    @PostMapping("/{callId}/reject")
    public ApiResponse<CallSessionResponse> reject(@PathVariable String callId,
                                                   @RequestParam(required = false) String reason) {
        return ApiResponse.<CallSessionResponse>builder()
                .result(callSessionService.reject(callId, currentUserId(), reason))
                .build();
    }

    @PostMapping("/{callId}/end")
    public ApiResponse<CallSessionResponse> end(@PathVariable String callId,
                                                @RequestParam(required = false) String reason) {
        return ApiResponse.<CallSessionResponse>builder()
                .result(callSessionService.end(callId, currentUserId(), reason))
                .build();
    }

    @PostMapping("/{callId}/connected")
    public ApiResponse<Void> connected(@PathVariable String callId) {
        callSessionService.markConnected(callId, currentUserId());
        return ApiResponse.<Void>builder().build();
    }

    @GetMapping("/ice-servers")
    public ApiResponse<IceServerResponse> iceServers() {
        return ApiResponse.<IceServerResponse>builder()
                .result(callSessionService.getIceServers())
                .build();
    }

    private String currentUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
