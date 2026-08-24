package com.otboo.domain.notification.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationKafkaConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    // Kafka Topic구독, docs/kafka/README.md에 맞춤
    @KafkaListener(
            topics = NotificationKafkaTopics.NOTIFICATION_CREATED,
            groupId = "otboo-notification-created-consumer"
    )
    public void consume(String payload) {
        NotificationCreatedMessage message = readMessage(payload);

        notificationService.createNotification(
                message.eventId(),
                message.receiverId(),
                message.title(),
                message.content(),
                message.level()
        );
    }

    // Producer에서 직렬화해서 보낸 json을 다시 역직렬화
    private NotificationCreatedMessage readMessage(String payload) {
        try {
            return objectMapper.readValue(payload, NotificationCreatedMessage.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("알림 Kafka 메시지 역직렬화 실패", exception);
        }
    }
}