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
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

  private static final long SSE_TIMEOUT = 60L * 60L * 1000L;

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

    return emitter;
  }

  public void sendToClient(UUID receiverId, String eventName, Object data) {
    // receiverId로 SSE 연결 찾기, 접속중일때만 실행
    sseEmitterRegistry.get(receiverId).ifPresent(session -> {
      try {
        session.getEmitter().send(
            SseEmitter.event()
                .name(eventName)
                .data(data)
        );
        session.touch();
      } catch (IOException exception) {
        sseEmitterRegistry.remove(receiverId, session);
      }
    });
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public NotificationDto createNotification(UUID receiverId, String title, String content, NotificationLevel level) {
    User receiver = userRepository.findById(receiverId)
        .orElseThrow(() -> new NotificationUserNotFoundException(receiverId));

    Notification notification = Notification.create(receiver, title, content, level);

    Notification savedNotification = notificationRepository.save(notification);

    NotificationDto notificationDto = NotificationMapper.toDto(savedNotification);

    sendToClient(receiverId, "notifications", notificationDto);

    return notificationDto;
  }
}
