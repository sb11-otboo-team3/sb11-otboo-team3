package com.otboo.domain.notification.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.otboo.domain.notification.entity.NotificationLevel;
import com.otboo.domain.notification.service.NotificationService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationKafkaConsumerTest {

  @Mock
  private NotificationService notificationService;

  private ObjectMapper objectMapper;
  private NotificationKafkaConsumer notificationKafkaConsumer;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    notificationKafkaConsumer = new NotificationKafkaConsumer(notificationService, objectMapper);
  }

  @Test
  @DisplayName("Kafka 메시지를 알림 생성 서비스로 전달한다")
  void consume_success() throws Exception {
    UUID receiverId = UUID.randomUUID();

    NotificationCreatedMessage message = new NotificationCreatedMessage(
        receiverId,
        "알림 제목",
        "알림 내용",
        NotificationLevel.WARNING,
        Instant.parse("2026-08-20T01:00:00Z")
    );

    String payload = objectMapper.writeValueAsString(message);

    notificationKafkaConsumer.consume(payload);

    verify(notificationService).createNotification(
        receiverId,
        "알림 제목",
        "알림 내용",
        NotificationLevel.WARNING
    );
  }

  @Test
  @DisplayName("역직렬화 실패 시 예외를 던지고 알림을 생성하지 않는다")
  void consume_invalidPayload_throwsException() {
    assertThatThrownBy(() -> notificationKafkaConsumer.consume("invalid-json"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("알림 Kafka 메시지 역직렬화 실패");

    verify(notificationService, never()).createNotification(
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any()
    );
  }
}