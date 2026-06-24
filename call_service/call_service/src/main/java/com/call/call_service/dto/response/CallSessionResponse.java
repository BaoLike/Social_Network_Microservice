package com.call.call_service.dto.response;

import com.call.call_service.entity.CallStatus;
import com.call.call_service.entity.CallType;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CallSessionResponse {
    String callId;
    String conversationId;
    String callerId;
    String calleeId;
    String callerName;
    String callerAvatar;
    CallType type;
    CallStatus status;
}
