package com.otboo.domain.notification.outbox;

import com.otboo.domain.notification.kafka.NotificationCreatedMessage;
import com.otboo.domain.notification.kafka.NotificationKafkaProducer;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationOutboxPublisher {

  private static final int MAX_RETRY_COUNT = 3;

  private final NotificationOutboxRepository notificationOutboxRepository;
  private final NotificationKafkaProducer notificationKafkaProducer;

  @Scheduled(fixedDelayString = "${app.notification.outbox.publish-delay-ms:3000}")
  @SchedulerLock(
      name = "notificationOutboxPublisher",
      lockAtMostFor = "PT30S",
      lockAtLeastFor = "PT1S"
  )
  @Transactional
  public void publishPending() {
    List<NotificationOutbox> outboxes =
        notificationOutboxRepository.findTop200ByStatusOrderByCreatedAtAsc(
            NotificationOutboxStatus.PENDING
        );

    for (NotificationOutbox outbox : outboxes) {
      publish(outbox);
    }
  }

  private void publish(NotificationOutbox outbox) {
    NotificationCreatedMessage message = new NotificationCreatedMessage(
        outbox.getReceiverId(),
        outbox.getTitle(),
        outbox.getContent(),
        outbox.getLevel(),
        outbox.getOccurredAt()
    );

    try {
      notificationKafkaProducer.send(message);
      outbox.markPublished();
    } catch (Exception exception) {
      if (outbox.getRetryCount() + 1 >= MAX_RETRY_COUNT) {
        outbox.markFailed();
      } else {
        outbox.markRetry();
      }

      log.warn(
          "알림 Outbox Kafka 발행 실패: outboxId={}, retryCount={}, status={}",
          outbox.getId(),
          outbox.getRetryCount(),
          outbox.getStatus(),
          exception
      );
    }
  }
}