package com.otboo.domain.profile.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FileDeletionOutboxTest {

  @Test
  @DisplayName("생성 시 PENDING 상태이고 재시도 횟수는 0이다")
  void createStartsAsPendingWithZeroRetryCount() {
    // when
    FileDeletionOutbox outbox = FileDeletionOutbox.create("profiles/user1/key.png");

    // then
    assertThat(outbox.getObjectKey()).isEqualTo("profiles/user1/key.png");
    assertThat(outbox.getStatus()).isEqualTo(FileDeletionStatus.PENDING);
    assertThat(outbox.getRetryCount()).isZero();
  }

  @Test
  @DisplayName("실패를 기록하면 재시도 횟수가 증가하고 에러 메시지가 저장된다")
  void recordFailureIncrementsRetryCountAndStoresMessage() {
    // given
    FileDeletionOutbox outbox = FileDeletionOutbox.create("profiles/user1/key.png");

    // when
    outbox.recordFailure("S3 연결 실패");

    // then
    assertThat(outbox.getRetryCount()).isEqualTo(1);
    assertThat(outbox.getLastErrorMessage()).isEqualTo("S3 연결 실패");
  }

  @Test
  @DisplayName("성공 처리하면 상태가 SUCCEEDED로 바뀌고 완료 시각이 기록된다")
  void markSucceededUpdatesStatusAndDeletedAt() {
    // given
    FileDeletionOutbox outbox = FileDeletionOutbox.create("profiles/user1/key.png");

    // when
    outbox.markSucceeded();

    // then
    assertThat(outbox.getStatus()).isEqualTo(FileDeletionStatus.SUCCEEDED);
    assertThat(outbox.getDeletedAt()).isNotNull();
  }

  @Test
  @DisplayName("재시도 횟수가 최대치 미만이면 초과 판정을 하지 않는다")
  void exceededMaxAttemptsReturnsFalseBeforeLimit() {
    // given
    FileDeletionOutbox outbox = FileDeletionOutbox.create("profiles/user1/key.png");
    outbox.recordFailure("실패1");
    outbox.recordFailure("실패2");

    // when & then
    assertThat(outbox.exceededMaxAttempts(5)).isFalse();
  }

  @Test
  @DisplayName("재시도 횟수가 최대치에 도달하면 초과 판정을 한다")
  void exceededMaxAttemptsReturnsTrueAtLimit() {
    // given
    FileDeletionOutbox outbox = FileDeletionOutbox.create("profiles/user1/key.png");
    for (int i = 0; i < 5; i++) {
      outbox.recordFailure("실패" + i);
    }

    // when & then
    assertThat(outbox.exceededMaxAttempts(5)).isTrue();
  }

  @Test
  @DisplayName("영구 실패 처리하면 상태가 FAILED로 바뀐다")
  void markFailedPermanentlyUpdatesStatus() {
    // given
    FileDeletionOutbox outbox = FileDeletionOutbox.create("profiles/user1/key.png");

    // when
    outbox.markFailedPermanently();

    // then
    assertThat(outbox.getStatus()).isEqualTo(FileDeletionStatus.FAILED);
  }
}