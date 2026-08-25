package com.otboo.domain.notification.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.notification.service.NotificationService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataIntegrityViolationException;
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

    public void consume(ConsumerRecord<String, String> record) {

        NotificationCreatedMessage message = readMessage(record.value());

        UUID eventId = resolveEventId(message, record);

        try {
            notificationService.createNotification(
                    eventId,
                    message.receiverId(),
                    message.title(),
                    message.content(),
                    message.level()
            );
        } catch (DataIntegrityViolationException exception) {
            notificationService.createNotification(
                    eventId,
                    message.receiverId(),
                    message.title(),
                    message.content(),
                    message.level()
            );
        }
    }

    private UUID resolveEventId(
            NotificationCreatedMessage message,
            ConsumerRecord<String, String> record
    ) {
        if (message.eventId() != null) {
            return message.eventId();
        }

        String legacyRecordId =
                "legacy-kafka:"
                        + record.topic()
                        + ":"
                        + record.partition()
                        + ":"
                        + record.offset();

        return UUID.nameUUIDFromBytes(
                legacyRecordId.getBytes(StandardCharsets.UTF_8)
        );
    }

    private NotificationCreatedMessage readMessage(String payload) {
        try {
            return objectMapper.readValue(
                    payload,
                    NotificationCreatedMessage.class
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "알림 Kafka 메시지 역직렬화 실패",
                    exception
            );
        }
    }
}