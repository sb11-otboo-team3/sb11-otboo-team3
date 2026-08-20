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

  // 알림 이벤트를 Outbox에 저장
  // 트랜잭션 안에서 발행된 이벤트라면 원본 트랜잭션과 함께 커밋/롤백
  @EventListener
  public void handle(NotificationEvent event) {
    notificationOutboxService.save(event);
  }
}
