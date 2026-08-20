package com.otboo.domain.notification.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.notification.entity.NotificationLevel;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationOutboxTest {

  @Test
  @DisplayName("Outbox 생성 시 기본 상태는 PENDING이다")
  void create_success() {
    UUID receiverId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-20T01:00:00Z");

    NotificationOutbox outbox = NotificationOutbox.create(
        receiverId,
        "알림 제목",
        "알림 내용",
        NotificationLevel.INFO,
        occurredAt
    );

    assertThat(outbox.getReceiverId()).isEqualTo(receiverId);
    assertThat(outbox.getTitle()).isEqualTo("알림 제목");
    assertThat(outbox.getContent()).isEqualTo("알림 내용");
    assertThat(outbox.getLevel()).isEqualTo(NotificationLevel.INFO);
    assertThat(outbox.getOccurredAt()).isEqualTo(occurredAt);
    assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.PENDING);
    assertThat(outbox.getRetryCount()).isZero();
    assertThat(outbox.getPublishedAt()).isNull();
    assertThat(outbox.getUpdatedAt()).isNotNull();
  }

  @Test
  @DisplayName("발행 성공 처리 시 PUBLISHED 상태가 된다")
  void markPublished_success() {
    NotificationOutbox outbox = createOutbox();

    outbox.markPublished();

    assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.PUBLISHED);
    assertThat(outbox.getPublishedAt()).isNotNull();
    assertThat(outbox.getUpdatedAt()).isNotNull();
  }

  @Test
  @DisplayName("재시도 처리 시 retryCount가 증가한다")
  void markRetry_success() {
    NotificationOutbox outbox = createOutbox();

    outbox.markRetry();

    assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.PENDING);
    assertThat(outbox.getRetryCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("최종 실패 처리 시 FAILED 상태가 된다")
  void markFailed_success() {
    NotificationOutbox outbox = createOutbox();

    outbox.markFailed();

    assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.FAILED);
    assertThat(outbox.getRetryCount()).isEqualTo(1);
  }

  private NotificationOutbox createOutbox() {
    return NotificationOutbox.create(
        UUID.randomUUID(),
        "알림 제목",
        "알림 내용",
        NotificationLevel.INFO,
        Instant.now()
    );
  }
}