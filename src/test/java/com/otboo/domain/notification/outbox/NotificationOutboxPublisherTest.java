package com.otboo.domain.notification.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.otboo.domain.notification.entity.NotificationLevel;
import com.otboo.domain.notification.kafka.NotificationCreatedMessage;
import com.otboo.domain.notification.kafka.NotificationKafkaProducer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxPublisherTest {

  @Mock
  private NotificationOutboxRepository notificationOutboxRepository;

  @Mock
  private NotificationKafkaProducer notificationKafkaProducer;

  @InjectMocks
  private NotificationOutboxPublisher notificationOutboxPublisher;

  @Test
  @DisplayName("PENDING Outbox를 Kafka로 발행하고 PUBLISHED 상태로 변경한다")
  void publishPending_success() {
    NotificationOutbox outbox = createOutbox();

    given(notificationOutboxRepository.findPendingOrderByCreatedAtAsc(10))
        .willReturn(List.of(outbox));

    notificationOutboxPublisher.publishPending();

    ArgumentCaptor<NotificationCreatedMessage> captor =
        ArgumentCaptor.forClass(NotificationCreatedMessage.class);

    verify(notificationKafkaProducer).send(captor.capture());

    NotificationCreatedMessage message = captor.getValue();

    assertThat(message.receiverId()).isEqualTo(outbox.getReceiverId());
    assertThat(message.title()).isEqualTo(outbox.getTitle());
    assertThat(message.content()).isEqualTo(outbox.getContent());
    assertThat(message.level()).isEqualTo(outbox.getLevel());
    assertThat(message.occurredAt()).isEqualTo(outbox.getOccurredAt());

    assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.PUBLISHED);
    assertThat(outbox.getPublishedAt()).isNotNull();
  }

  @Test
  @DisplayName("Kafka 발행 실패 시 재시도 횟수를 증가시킨다")
  void publishPending_sendFail_retry() {
    NotificationOutbox outbox = createOutbox();

    given(notificationOutboxRepository.findPendingOrderByCreatedAtAsc(10))
        .willReturn(List.of(outbox));

    willThrow(new IllegalStateException("Kafka 발행 실패"))
        .given(notificationKafkaProducer)
        .send(any(NotificationCreatedMessage.class));

    notificationOutboxPublisher.publishPending();

    assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.PENDING);
    assertThat(outbox.getRetryCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("재시도 한도 초과 시 FAILED 상태로 변경한다")
  void publishPending_sendFail_markFailed() {
    NotificationOutbox outbox = createOutbox();
    outbox.markRetry();
    outbox.markRetry();

    given(notificationOutboxRepository.findPendingOrderByCreatedAtAsc(10))
        .willReturn(List.of(outbox));

    willThrow(new IllegalStateException("Kafka 발행 실패"))
        .given(notificationKafkaProducer)
        .send(any(NotificationCreatedMessage.class));

    notificationOutboxPublisher.publishPending();

    assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.FAILED);
    assertThat(outbox.getRetryCount()).isEqualTo(3);
  }

  private NotificationOutbox createOutbox() {
    return NotificationOutbox.create(
        UUID.randomUUID(),
        "알림 제목",
        "알림 내용",
        NotificationLevel.INFO,
        Instant.parse("2026-08-20T01:00:00Z")
    );
  }
}