package com.otboo.global.infrastructure.storage.event;

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

  @Retryable(
      retryFor = StorageDeleteException.class,
      maxAttempts = MAX_ATTEMPTS,
      backoff = @Backoff(delayExpression = "${storage.deletion.retry.backoff-ms:1000}", multiplier = 2)
  )

  public void deleteWithRetry(String objectKey) {
    fileStorage.delete(objectKey);
  }

  // 최대 재시도 후에도 계속 실패하면 여기로 떨어진다.
  // 지금은 에러 로깅만 하고, 실패 이력 영속화/재처리(Outbox)는 #109에서 다룬다.
  @Recover
  public void recover(StorageDeleteException e, String objectKey) {
    log.error(
        "파일 삭제가 {}회 재시도 후에도 실패했습니다. 수동 정리가 필요합니다. objectKey={}",
        MAX_ATTEMPTS, objectKey, e
    );
  }
}