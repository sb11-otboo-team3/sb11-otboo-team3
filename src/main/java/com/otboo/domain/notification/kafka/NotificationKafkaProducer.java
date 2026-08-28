package com.otboo.domain.notification.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationKafkaProducer {

  private static final long SEND_TIMEOUT_SECONDS = 5L;

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;

  public void send(NotificationCreatedMessage message) {
    String payload;
    try {
      payload = objectMapper.writeValueAsString(message);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("알림 Kafka 메시지 직렬화 실패", exception);
    }

    try {
      kafkaTemplate.send(
          NotificationKafkaTopics.NOTIFICATION_CREATED,
          message.receiverId().toString(),
          payload
      ).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (Exception exception) {
      throw new IllegalStateException("알림 Kafka 메시지 발행 실패", exception);
    }
  }
}