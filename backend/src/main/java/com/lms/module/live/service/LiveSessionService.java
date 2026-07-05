package com.lms.module.live.service;

import com.lms.common.exception.BusinessLogicException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.kafka.event.BaseEvent;
import com.lms.kafka.producer.KafkaProducerService;
import com.lms.module.live.entity.LiveSession;
import com.lms.module.live.repository.LiveSessionRepository;
import com.lms.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LiveSessionService {

    private final LiveSessionRepository liveSessionRepository;
    private final KafkaProducerService kafkaProducer;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public LiveSession schedule(Map<String, Object> request, UUID instructorId) {
        LiveSession session = LiveSession.builder()
                .courseId(UUID.fromString((String) request.get("courseId")))
                .instructorId(instructorId)
                .title((String) request.get("title"))
                .description((String) request.get("description"))
                .scheduledAt(LocalDateTime.parse((String) request.get("scheduledAt")))
                .status(LiveSession.SessionStatus.SCHEDULED)
                .build();
        return liveSessionRepository.save(session);
    }

    @Transactional
    public LiveSession startSession(UUID sessionId, UUID instructorId) {
        LiveSession session = getById(sessionId);
        if (!session.getInstructorId().equals(instructorId)) {
            throw new BusinessLogicException("Only the session instructor can start this session");
        }
        if (session.getStatus() != LiveSession.SessionStatus.SCHEDULED) {
            throw new BusinessLogicException("Session is not in SCHEDULED state");
        }

        UUID roomId = UUID.randomUUID();
        session.setRoomId(roomId);
        session.setStartedAt(LocalDateTime.now());
        session.setStatus(LiveSession.SessionStatus.LIVE);
        LiveSession saved = liveSessionRepository.save(session);

        // Track participants in Redis
        redisTemplate.opsForSet().add("session:" + roomId + ":participants", instructorId.toString());

        // Publish Kafka event for notifications to enrolled students
        kafkaProducer.publishLiveSessionStarted(new BaseEvent.LiveSessionStartedEvent(
                UUID.randomUUID().toString(), TenantContext.getCurrentTenant(), LocalDateTime.now(),
                sessionId, session.getCourseId(), instructorId, session.getTitle(), roomId
        ));

        log.info("Live session {} started with roomId {}", sessionId, roomId);
        return saved;
    }

    @Transactional
    public LiveSession endSession(UUID sessionId, UUID instructorId) {
        LiveSession session = getById(sessionId);
        if (!session.getInstructorId().equals(instructorId)) {
            throw new BusinessLogicException("Only the session instructor can end this session");
        }
        session.setEndedAt(LocalDateTime.now());
        session.setStatus(LiveSession.SessionStatus.ENDED);
        LiveSession saved = liveSessionRepository.save(session);

        // Clean up Redis
        if (session.getRoomId() != null) {
            redisTemplate.delete("session:" + session.getRoomId() + ":participants");
        }

        kafkaProducer.publishLiveSessionEnded(new BaseEvent.LiveSessionEndedEvent(
                UUID.randomUUID().toString(), TenantContext.getCurrentTenant(), LocalDateTime.now(),
                sessionId, session.getCourseId()
        ));

        return saved;
    }

    public List<LiveSession> getCourseSessions(UUID courseId) {
        return liveSessionRepository.findByCourseIdOrderByScheduledAtDesc(courseId);
    }

    public List<LiveSession> getAllSessions() {
        return liveSessionRepository.findByStatusInOrderByScheduledAtDesc(
            List.of(LiveSession.SessionStatus.SCHEDULED, LiveSession.SessionStatus.LIVE)
        );
    }

    private LiveSession getById(UUID id) {
        return liveSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LiveSession", id.toString()));
    }
}
