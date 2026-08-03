package com.otboo.domain.notification.event;

import com.otboo.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {

  private final NotificationService notificationService;

  // commit이 성공한 뒤에 알림을 생성하기(알림왔는데 변화없는거 방지)
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
