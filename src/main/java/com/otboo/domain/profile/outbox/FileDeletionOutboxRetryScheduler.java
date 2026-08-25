package com.otboo.domain.profile.outbox;

import com.otboo.global.infrastructure.storage.FileStorage;
import com.otboo.global.infrastructure.storage.exception.StorageDeleteException;
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
public class FileDeletionOutboxRetryScheduler {

  private static final int MAX_ATTEMPTS = 5;

  private final FileDeletionOutboxRepository fileDeletionOutboxRepository;
  private final FileStorage fileStorage;

  // 매 10분마다 Outbox에 쌓인 실패 건을 재처리한다. 즉시 재시도(Spring Retry)가
  // 모두 실패한 뒤에도 이 스케줄러가 계속 시도하므로, 일시적인 S3 장애가 복구되면
  // 결국 삭제가 완료된다. (#109)
  @Scheduled(cron = "${storage.deletion.outbox.retry-cron:0 */10 * * * *}", zone = "Asia/Seoul")
  @SchedulerLock(name = "fileDeletionOutboxRetry", lockAtMostFor = "PT5M", lockAtLeastFor = "PT10S")
  @Transactional
  public void retryPendingDeletions() {
    List<FileDeletionOutbox> pendingItems =
        fileDeletionOutboxRepository.findTop50ByStatusOrderByCreatedAtAsc(FileDeletionStatus.PENDING);

    if (pendingItems.isEmpty()) {
      return;
    }

    log.info("Outbox 파일 삭제 재처리를 시작합니다. 대상 건수={}", pendingItems.size());

    for (FileDeletionOutbox outbox : pendingItems) {
      retryOne(outbox);
    }
  }

  private void retryOne(FileDeletionOutbox outbox) {
    try {
      fileStorage.delete(outbox.getObjectKey());
      outbox.markSucceeded();
      log.info("Outbox 재처리로 파일 삭제에 성공했습니다. objectKey={}", outbox.getObjectKey());
    } catch (StorageDeleteException e) {
      outbox.recordFailure(e.getMessage());
      if (outbox.exceededMaxAttempts(MAX_ATTEMPTS)) {
        outbox.markFailedPermanently();
        log.error(
            "Outbox 재처리가 {}회를 초과해 실패했습니다. 수동 확인이 필요합니다. objectKey={}",
            MAX_ATTEMPTS, outbox.getObjectKey(), e
        );
      } else {
        log.warn(
            "Outbox 재처리에 실패했습니다. 다음 주기에 다시 시도합니다. objectKey={}, retryCount={}",
            outbox.getObjectKey(), outbox.getRetryCount(), e
        );
      }
    }
  }
}