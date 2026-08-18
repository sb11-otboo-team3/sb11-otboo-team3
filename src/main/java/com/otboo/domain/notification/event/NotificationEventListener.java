package com.otboo.domain.notification.event;

import com.otboo.domain.notification.kafka.NotificationKafkaProducer;
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

  private final NotificationKafkaProducer notificationKafkaProducer;

  // 원본 트랜잭션 커밋 이후 알림 생성/전송을 비동기로 처리
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(NotificationEvent event) {
    notificationKafkaProducer.send(event);
  }
}
