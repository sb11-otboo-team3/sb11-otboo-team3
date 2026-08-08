package com.otboo.global.infrastructure.storage.event;

import com.otboo.global.infrastructure.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileReplacementEventListener {

  private final FileDeletionRetryService fileDeletionRetryService;

  // 트랜잭션 커밋 성공 시: 이제 쓸모없어진 기존 파일을 정리한다.
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleAfterCommit(FileReplacementEvent event) {
    if (event.oldObjectKey() == null || event.oldObjectKey().isBlank()) {
      return;
    }
    fileDeletionRetryService.deleteWithRetry(event.oldObjectKey());
  }

  // 트랜잭션 롤백 시: DB 반영이 안 됐으므로, 새로 업로드했던 파일이 orphan이 되지 않도록 정리한다.
  @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
  public void handleAfterRollback(FileReplacementEvent event) {
    if (event.newObjectKey() == null || event.newObjectKey().isBlank()) {
      return;
    }
    fileDeletionRetryService.deleteWithRetry(event.newObjectKey());
  }
}