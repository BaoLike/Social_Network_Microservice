package com.call.call_service.service.impl;

import com.call.call_service.entity.CallWebSocketSession;
import com.call.call_service.repository.CallWebSocketSessionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CallWebSocketSessionService {
    CallWebSocketSessionRepository repository;

    public CallWebSocketSession create(CallWebSocketSession session) {
        return repository.save(session);
    }

    public void deleteSession(String socketSessionId) {
        repository.deleteBySocketSessionId(socketSessionId);
    }
}
