package com.otboo.domain.notification.event;

import com.otboo.domain.notification.outbox.NotificationOutboxService;
import com.otboo.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

  private final NotificationOutboxService notificationOutboxService;
  private final NotificationService notificationService;
  // 알림 이벤트를 Outbox에 저장
  // 트랜잭션 안에서 발행된 이벤트라면 원본 트랜잭션과 함께 커밋/롤백
//  @EventListener
//  public void handle(NotificationEvent event) {
//    notificationOutboxService.save(event);
//  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(NotificationEvent event) {
    notificationService.createNotification(
        event.receiverId(),
        event.title(),
        event.content(),
        event.level()
    );
  }
}
