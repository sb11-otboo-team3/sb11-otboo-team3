package com.otboo.domain.profile.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.otboo.global.infrastructure.storage.FileStorage;
import com.otboo.global.infrastructure.storage.exception.StorageDeleteException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileDeletionOutboxRetrySchedulerTest {

  @Mock
  private FileDeletionOutboxRepository fileDeletionOutboxRepository;

  @Mock
  private FileStorage fileStorage;

  @InjectMocks
  private FileDeletionOutboxRetryScheduler scheduler;

  @Test
  @DisplayName("대기 중인 항목이 없으면 아무 작업도 하지 않는다")
  void retryPendingDeletionsDoesNothingWhenEmpty() {
    // given
    given(fileDeletionOutboxRepository.findTop50ByStatusOrderByCreatedAtAsc(FileDeletionStatus.PENDING))
        .willReturn(List.of());

    // when
    scheduler.retryPendingDeletions();

    // then
    verify(fileStorage, org.mockito.Mockito.never()).delete(any());
  }

  @Test
  @DisplayName("삭제에 성공하면 SUCCEEDED로 상태를 변경한다")
  void retryPendingDeletionsMarksSucceededOnSuccess() {
    // given
    FileDeletionOutbox outbox = FileDeletionOutbox.create("profiles/user1/key.png");
    given(fileDeletionOutboxRepository.findTop50ByStatusOrderByCreatedAtAsc(FileDeletionStatus.PENDING))
        .willReturn(List.of(outbox));
    willDoNothing().given(fileStorage).delete("profiles/user1/key.png");

    // when
    scheduler.retryPendingDeletions();

    // then
    assertThat(outbox.getStatus()).isEqualTo(FileDeletionStatus.SUCCEEDED);
  }

  @Test
  @DisplayName("삭제가 계속 실패하고 최대 재시도를 초과하면 FAILED로 상태를 변경한다")
  void retryPendingDeletionsMarksFailedWhenExceedingMaxAttempts() {
    // given
    FileDeletionOutbox outbox = FileDeletionOutbox.create("profiles/user1/key.png");
    for (int i = 0; i < 4; i++) {
      outbox.recordFailure("이전 실패 " + i);
    }
    given(fileDeletionOutboxRepository.findTop50ByStatusOrderByCreatedAtAsc(FileDeletionStatus.PENDING))
        .willReturn(List.of(outbox));
    willThrow(new StorageDeleteException(new RuntimeException("S3 장애")))
        .given(fileStorage).delete("profiles/user1/key.png");

    // when: 5번째 실패로 최대치(5)에 도달
    scheduler.retryPendingDeletions();

    // then
    assertThat(outbox.getStatus()).isEqualTo(FileDeletionStatus.FAILED);
    assertThat(outbox.getRetryCount()).isEqualTo(5);
  }

  @Test
  @DisplayName("삭제가 실패했지만 최대 재시도 미만이면 PENDING 상태를 유지한다")
  void retryPendingDeletionsKeepsPendingWhenBelowMaxAttempts() {
    // given
    FileDeletionOutbox outbox = FileDeletionOutbox.create("profiles/user1/key.png");
    given(fileDeletionOutboxRepository.findTop50ByStatusOrderByCreatedAtAsc(FileDeletionStatus.PENDING))
        .willReturn(List.of(outbox));
    willThrow(new StorageDeleteException(new RuntimeException("S3 장애")))
        .given(fileStorage).delete("profiles/user1/key.png");

    // when
    scheduler.retryPendingDeletions();

    // then
    assertThat(outbox.getStatus()).isEqualTo(FileDeletionStatus.PENDING);
    assertThat(outbox.getRetryCount()).isEqualTo(1);
  }
}