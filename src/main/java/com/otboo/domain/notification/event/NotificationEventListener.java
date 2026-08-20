package com.otboo.domain.notification.event;

import com.otboo.domain.notification.outbox.NotificationOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

  private final NotificationOutboxService notificationOutboxService;

  // 원본 트랜잭션 커밋 이후 알림 생성/전송을 비동기로 처리
  @EventListener
  public void handle(NotificationEvent event) {
    notificationOutboxService.save(event);
  }
}
