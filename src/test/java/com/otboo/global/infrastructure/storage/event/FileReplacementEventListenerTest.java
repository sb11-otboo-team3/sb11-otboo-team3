package com.otboo.global.infrastructure.storage.event;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileReplacementEventListenerTest {

  @Mock
  private FileDeletionRetryService fileDeletionRetryService;

  @InjectMocks
  private FileReplacementEventListener listener;

  @Test
  @DisplayName("커밋 후 이벤트를 받으면 기존 Object Key 삭제를 시도한다")
  void handleAfterCommitDeletesOldObjectKey() {
    // given
    FileReplacementEvent event = new FileReplacementEvent("old-key.png", "new-key.png");

    // when
    listener.handleAfterCommit(event);

    // then
    verify(fileDeletionRetryService).deleteWithRetry("old-key.png");
  }

  @Test
  @DisplayName("커밋 후 이벤트의 기존 Object Key가 없으면 삭제를 시도하지 않는다")
  void handleAfterCommitWithNullOldKeyDoesNothing() {
    // given
    FileReplacementEvent event = new FileReplacementEvent(null, "new-key.png");

    // when
    listener.handleAfterCommit(event);

    // then
    verify(fileDeletionRetryService, never()).deleteWithRetry(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("롤백 후 이벤트를 받으면 새로 업로드된 Object Key 삭제를 시도한다")
  void handleAfterRollbackDeletesNewObjectKey() {
    // given
    FileReplacementEvent event = new FileReplacementEvent("old-key.png", "new-key.png");

    // when
    listener.handleAfterRollback(event);

    // then
    verify(fileDeletionRetryService).deleteWithRetry("new-key.png");
  }

  @Test
  @DisplayName("롤백 후 이벤트의 새 Object Key가 없으면 삭제를 시도하지 않는다")
  void handleAfterRollbackWithNullNewKeyDoesNothing() {
    // given
    FileReplacementEvent event = new FileReplacementEvent("old-key.png", null);

    // when
    listener.handleAfterRollback(event);

    // then
    verify(fileDeletionRetryService, never()).deleteWithRetry(org.mockito.ArgumentMatchers.any());
  }
}