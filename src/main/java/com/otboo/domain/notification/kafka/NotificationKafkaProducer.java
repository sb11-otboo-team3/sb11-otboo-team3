package com.otboo.domain.notification.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.notification.event.NotificationEvent;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationKafkaProducer {

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;

  public void send(NotificationEvent event) {
    NotificationCreatedMessage message = new NotificationCreatedMessage(
        event.receiverId(),
        event.title(),
        event.content(),
        event.level(),
        Instant.now()
    );

    String payload;
    try {
      payload = objectMapper.writeValueAsString(message);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize notification message", e);
      throw new IllegalStateException("알림 Kafka 메시지 직렬화 실패", e);
    }

    kafkaTemplate.send(
        NotificationKafkaTopics.NOTIFICATION_CREATED,
        event.receiverId().toString(),
        payload
    ).whenComplete((result, exception) -> {
      if (exception != null) {
        log.error(
            "Failed to send notification message: receiverId={}, topic={}",
            event.receiverId(),
            NotificationKafkaTopics.NOTIFICATION_CREATED,
            exception
        );
        return;
      }

      log.debug(
          "Notification message sent: receiverId={}, topic={}",
          event.receiverId(),
          NotificationKafkaTopics.NOTIFICATION_CREATED
      );
    });
  }
}