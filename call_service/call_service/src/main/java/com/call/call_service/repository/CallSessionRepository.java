package com.call.call_service.repository;

import com.call.call_service.entity.CallSession;
import com.call.call_service.entity.CallStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface CallSessionRepository extends MongoRepository<CallSession, String> {
    List<CallSession> findByCallerIdOrCalleeIdOrderByCreatedAtDesc(String callerId, String calleeId);
    List<CallSession> findByStatusAndCreatedAtBefore(CallStatus status, Instant before);
}
