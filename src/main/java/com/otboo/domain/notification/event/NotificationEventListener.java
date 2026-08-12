package com.otboo.domain.notification.event;

import com.otboo.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

  private final NotificationService notificationService;

  // 원본 트랜잭션 커밋 이후 알림 생성/전송을 비동기로 처리
  @Async("notificationTaskExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(NotificationEvent event) {
    try {
      notificationService.createNotification(
          event.receiverId(),
          event.title(),
          event.content(),
          event.level()
      );
    } catch (Exception e) {
      log.warn(
          "알림 이벤트 비동기 처리 실패: receiverId={}, title={}",
          event.receiverId(),
          event.title(),
          e
      );
    }
  }
}
