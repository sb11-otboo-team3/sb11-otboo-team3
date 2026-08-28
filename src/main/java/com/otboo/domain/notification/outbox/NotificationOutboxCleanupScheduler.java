package com.otboo.domain.notification.outbox;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationOutboxCleanupScheduler {

  // 매일 03:30실행
  // PUBLISHED 상태이고 updatedAt이 하루 지난 것 삭제
  // FAILED 상태이고 updatedAt이 7일 지난 것 삭제
  // PENDING은 삭제하지 않음
  private static final Duration PUBLISHED_RETENTION = Duration.ofDays(1);
  private static final Duration FAILED_RETENTION = Duration.ofDays(7);

  private final NotificationOutboxRepository notificationOutboxRepository;

  @Scheduled(cron = "${app.notification.outbox.cleanup-cron:0 30 21 * * *}", zone = "Asia/Seoul")
  @SchedulerLock(
      name = "notificationOutboxCleanupScheduler",
      lockAtMostFor = "PT10M",
      lockAtLeastFor = "PT10S"
  )
  @Transactional
  public void cleanup() {
    Instant now = Instant.now();

    long deletedPublished = notificationOutboxRepository.deleteByStatusAndUpdatedAtBefore(
        NotificationOutboxStatus.PUBLISHED,
        now.minus(PUBLISHED_RETENTION)
    );

    long deletedFailed = notificationOutboxRepository.deleteByStatusAndUpdatedAtBefore(
        NotificationOutboxStatus.FAILED,
        now.minus(FAILED_RETENTION)
    );

    log.info(
        "알림 Outbox 정리 완료: deletedPublished={}, deletedFailed={}",
        deletedPublished,
        deletedFailed
    );
  }
}