package com.call.call_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InitiateCallRequest {
    @NotBlank
    String calleeId;

    String conversationId;
    String callerName;
    String callerAvatar;
}
