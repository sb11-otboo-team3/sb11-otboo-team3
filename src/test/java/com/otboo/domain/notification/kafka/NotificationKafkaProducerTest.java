package com.otboo.domain.notification.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.otboo.domain.notification.entity.NotificationLevel;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class NotificationKafkaProducerTest {

  @Mock
  private KafkaTemplate<String, String> kafkaTemplate;

  private ObjectMapper objectMapper;
  private NotificationKafkaProducer notificationKafkaProducer;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    notificationKafkaProducer = new NotificationKafkaProducer(kafkaTemplate, objectMapper);
  }

  @Test
  @DisplayName("알림 메시지를 Kafka topic으로 발행한다")
  void send_success() throws Exception {
    UUID receiverId = UUID.randomUUID();

    NotificationCreatedMessage message = new NotificationCreatedMessage(
        receiverId,
        "알림 제목",
        "알림 내용",
        NotificationLevel.INFO,
        Instant.parse("2026-08-20T01:00:00Z")
    );

    CompletableFuture<SendResult<String, String>> future =
        CompletableFuture.completedFuture(null);

    given(kafkaTemplate.send(
        eq(NotificationKafkaTopics.NOTIFICATION_CREATED),
        eq(receiverId.toString()),
        anyString()
    )).willReturn(future);

    notificationKafkaProducer.send(message);

    ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

    verify(kafkaTemplate).send(
        eq(NotificationKafkaTopics.NOTIFICATION_CREATED),
        eq(receiverId.toString()),
        payloadCaptor.capture()
    );

    NotificationCreatedMessage payload =
        objectMapper.readValue(payloadCaptor.getValue(), NotificationCreatedMessage.class);

    assertThat(payload.receiverId()).isEqualTo(receiverId);
    assertThat(payload.title()).isEqualTo("알림 제목");
    assertThat(payload.content()).isEqualTo("알림 내용");
    assertThat(payload.level()).isEqualTo(NotificationLevel.INFO);
    assertThat(payload.occurredAt()).isEqualTo(Instant.parse("2026-08-20T01:00:00Z"));
  }

  @Test
  @DisplayName("Kafka 발행 실패 시 예외를 던진다")
  void send_fail_throwsException() {
    UUID receiverId = UUID.randomUUID();

    NotificationCreatedMessage message = new NotificationCreatedMessage(
        receiverId,
        "알림 제목",
        "알림 내용",
        NotificationLevel.INFO,
        Instant.now()
    );

    CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
    future.completeExceptionally(new RuntimeException("Kafka failure"));

    given(kafkaTemplate.send(
        eq(NotificationKafkaTopics.NOTIFICATION_CREATED),
        eq(receiverId.toString()),
        anyString()
    )).willReturn(future);

    assertThatThrownBy(() -> notificationKafkaProducer.send(message))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("알림 Kafka 메시지 발행 실패");
  }
}