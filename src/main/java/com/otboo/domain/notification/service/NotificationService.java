package com.otboo.domain.notification.service;

import com.otboo.domain.notification.dto.response.NotificationDto;
import com.otboo.domain.notification.dto.response.NotificationDtoCursorResponse;
import com.otboo.domain.notification.entity.Notification;
import com.otboo.domain.notification.entity.NotificationLevel;
import com.otboo.domain.notification.exception.InvalidNotificationCursorException;
import com.otboo.domain.notification.exception.NotificationForbiddenException;
import com.otboo.domain.notification.exception.NotificationNotFoundException;
import com.otboo.domain.notification.exception.NotificationUserNotFoundException;
import com.otboo.domain.notification.mapper.NotificationMapper;
import com.otboo.domain.notification.repository.NotificationRepository;
import com.otboo.domain.notification.sse.SseEmitterRegistry;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

  // 장시간 SSE 연결 유지를 위해 서버 자체 timeout을 사용하지 않음
  private static final long SSE_TIMEOUT = 0L;
  // 알림 재전송 상한 100개
  private static final int SSE_REPLAY_LIMIT = 100;

  private final NotificationRepository notificationRepository;
  private final SseEmitterRegistry sseEmitterRegistry;
  private final UserRepository userRepository;

  public NotificationDtoCursorResponse getNotifications(
      String cursor, UUID idAfter, int limit, UUID currentUserId
  ) {
    validateCursor(cursor, idAfter);

    List<Notification> notifications = notificationRepository.findNotifications(
        currentUserId,
        cursor,
        idAfter,
        limit + 1
    );

    boolean hasNext = notifications.size() > limit;

    if(hasNext){
      notifications = notifications.subList(0, limit);
    }

    List<NotificationDto> data = notifications.stream()
        .map(NotificationMapper::toDto)
        .toList();

    String nextCursor = null;
    UUID nextIdAfter = null;

    if (hasNext) {
      Notification last = notifications.get(notifications.size() - 1);
      nextCursor = last.getCreatedAt().toString();
      nextIdAfter = last.getId();
    }

    long totalCount = notificationRepository.countNotifications(currentUserId);

    return new NotificationDtoCursorResponse(
        data,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        "createdAt",
        "DESCENDING"
    );
  }

  @Transactional
  public void deleteNotification(UUID notificationId, UUID currentUserId) {
    Notification notification = notificationRepository.findById(notificationId)
        .orElseThrow(() -> new NotificationNotFoundException(notificationId));

    if (!notification.getReceiver().getId().equals(currentUserId)) {
      throw new NotificationForbiddenException();
    }

    notificationRepository.delete(notification);
  }

  // cursor와 idAfter는 둘 다 있거나 둘 다 없어야 함
  private void validateCursor(String cursor, UUID idAfter) {
    boolean hasCursor = cursor != null && !cursor.isBlank();
    boolean hasIdAfter = idAfter != null;

    if (hasCursor != hasIdAfter) {
      throw new InvalidNotificationCursorException();
    }

    // 시간형식인지 검사
    if(hasCursor){
      try {
        Instant.parse(cursor);
      } catch (DateTimeParseException e) {
        throw new InvalidNotificationCursorException();
      }
    }
  }

  public SseEmitter subscribe(UUID currentUserId, UUID lastEventId) {
    SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

    sseEmitterRegistry.add(currentUserId, emitter);

    sendToClient(
        currentUserId,
        "connected",
        "SSE 연결이 완료되었습니다."
    );

    if(lastEventId != null){
      List<Notification> missedNotifications =
          notificationRepository.findNotificationsAfter(currentUserId, lastEventId, SSE_REPLAY_LIMIT);

      for (Notification notification : missedNotifications) {
        NotificationDto notificationDto = NotificationMapper.toDto(notification);
        sendToClient(currentUserId, "notifications", notificationDto);
      }
    }

    return emitter;
  }

  public void sendToClient(UUID receiverId, String eventName, Object data) {
    // receiverId로 SSE 연결 찾기, 접속중일때만 실행
    sseEmitterRegistry.get(receiverId).ifPresent(session -> {
      try {
        SseEmitter.SseEventBuilder event = SseEmitter.event()
                .name(eventName)
                .data(data);

        if (data instanceof NotificationDto notificationDto) {
          event.id(notificationDto.id().toString());
        }

        session.getEmitter().send(event);
        session.touch();
      } catch (IOException exception) {
        sseEmitterRegistry.remove(receiverId, session);
      }
    });
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public NotificationDto createNotification(
          UUID eventId,
          UUID receiverId,
          String title,
          String content,
          NotificationLevel level
  ) {

    Optional<Notification> existingNotification =
            notificationRepository.findByEventId(eventId);

    if (existingNotification.isPresent()) {
      return NotificationMapper.toDto(
              existingNotification.get()
      );
    }

    User receiver = userRepository.findById(receiverId)
        .orElseThrow(() -> new NotificationUserNotFoundException(receiverId));

    Notification notification = Notification.create(eventId, receiver, title, content, level);

    Notification savedNotification = notificationRepository.saveAndFlush(notification);

    NotificationDto notificationDto = NotificationMapper.toDto(savedNotification);

    sendToClientAfterCommit(receiverId, notificationDto);

    return notificationDto;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public NotificationDto createNotification(
      UUID receiverId,
      String title,
      String content,
      NotificationLevel level
  ) {
    return createNotification(
        UUID.randomUUID(),
        receiverId,
        title,
        content,
        level
    );
  }

  private void sendToClientAfterCommit(UUID receiverId, NotificationDto notificationDto) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              sendToClient(receiverId, "notifications", notificationDto);
            }
          }
      );
      return;
    }

    sendToClient(receiverId, "notifications", notificationDto);
  }
}
