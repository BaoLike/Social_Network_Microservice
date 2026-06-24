package com.call.call_service.repository;

import com.call.call_service.entity.CallWebSocketSession;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CallWebSocketSessionRepository extends MongoRepository<CallWebSocketSession, String> {
    void deleteBySocketSessionId(String socketSessionId);
}
