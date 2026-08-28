package com.otboo.global.infrastructure.storage.event;

import com.otboo.domain.profile.outbox.FileDeletionOutbox;
import com.otboo.domain.profile.outbox.FileDeletionOutboxRepository;
import com.otboo.global.infrastructure.storage.FileStorage;
import com.otboo.global.infrastructure.storage.exception.StorageDeleteException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileDeletionRetryService {

  private static final int MAX_ATTEMPTS = 3;

  private final FileStorage fileStorage;
  private final FileDeletionOutboxRepository fileDeletionOutboxRepository;

  @Retryable(
      retryFor = StorageDeleteException.class,
      maxAttempts = MAX_ATTEMPTS,
      backoff = @Backoff(delayExpression = "${storage.deletion.retry.backoff-ms:1000}", multiplier = 2)
  )
  public void deleteWithRetry(String objectKey) {
    fileStorage.delete(objectKey);
  }

  // 즉시 재시도(MAX_ATTEMPTS회)가 모두 실패하면 여기로 떨어진다. 더 이상
  // 요청 처리 흐름을 붙잡지 않고, 실패 사실을 Outbox에 기록해 스케줄러가
  // 나중에 다시 시도하도록 위임한다. (#109)
  @Recover
  public void recover(StorageDeleteException e, String objectKey) {
    log.error(
        "파일 삭제가 {}회 재시도 후에도 실패했습니다. Outbox에 기록해 추후 재처리합니다. objectKey={}",
        MAX_ATTEMPTS, objectKey, e
    );
    FileDeletionOutbox outbox = FileDeletionOutbox.create(objectKey);
    outbox.recordFailure(e.getMessage());
    fileDeletionOutboxRepository.save(outbox);
  }
}