package com.otboo.domain.notification.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.otboo.domain.notification.entity.NotificationLevel;
import com.otboo.domain.notification.service.NotificationService;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

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

        notificationKafkaConsumer =
                new NotificationKafkaConsumer(
                        notificationService,
                        objectMapper
                );
    }

    @Test
    @DisplayName("Kafka 메시지를 알림 생성 서비스로 전달한다")
    void consume_success() throws Exception {

        // given
        UUID eventId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        NotificationCreatedMessage message =
                new NotificationCreatedMessage(
                        eventId,
                        receiverId,
                        "알림 제목",
                        "알림 내용",
                        NotificationLevel.WARNING,
                        Instant.parse("2026-08-20T01:00:00Z")
                );

        String payload = objectMapper.writeValueAsString(message);

        ConsumerRecord<String, String> record =
                new ConsumerRecord<>(
                        NotificationKafkaTopics.NOTIFICATION_CREATED,
                        1,
                        10L,
                        null,
                        payload
                );

        // when
        notificationKafkaConsumer.consume(record);

        // then
        verify(notificationService).createNotification(
                eventId,
                receiverId,
                "알림 제목",
                "알림 내용",
                NotificationLevel.WARNING
        );
    }

    @Test
    @DisplayName("역직렬화 실패 시 예외를 던지고 알림을 생성하지 않는다")
    void consume_invalidPayload_throwsException() {

        ConsumerRecord<String, String> record =
                new ConsumerRecord<>(
                        NotificationKafkaTopics.NOTIFICATION_CREATED,
                        1,
                        10L,
                        null,
                        "invalid-json"
                );

        assertThatThrownBy(() ->
                notificationKafkaConsumer.consume(record)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("알림 Kafka 메시지 역직렬화 실패");

        verify(notificationService, never())
                .createNotification(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any()
                );
    }

    @Test
    @DisplayName("eventId가 없는 기존 Kafka 메시지는 record 위치 기반 eventId를 사용한다")
    void consumeLegacyMessageWithoutEventIdUsesDeterministicEventId()
            throws Exception {

        // given
        UUID receiverId = UUID.randomUUID();

        NotificationCreatedMessage message =
                new NotificationCreatedMessage(
                        null,
                        receiverId,
                        "기존 알림",
                        "기존 메시지",
                        NotificationLevel.INFO,
                        Instant.parse("2026-08-20T01:00:00Z")
                );

        String payload = objectMapper.writeValueAsString(message);

        ConsumerRecord<String, String> record =
                new ConsumerRecord<>(
                        NotificationKafkaTopics.NOTIFICATION_CREATED,
                        2,
                        123L,
                        null,
                        payload
                );

        // when
        notificationKafkaConsumer.consume(record);
        notificationKafkaConsumer.consume(record);

        // then
        ArgumentCaptor<UUID> eventIdCaptor =
                ArgumentCaptor.forClass(UUID.class);

        verify(
                notificationService,
                org.mockito.Mockito.times(2)
        ).createNotification(
                eventIdCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(receiverId),
                org.mockito.ArgumentMatchers.eq("기존 알림"),
                org.mockito.ArgumentMatchers.eq("기존 메시지"),
                org.mockito.ArgumentMatchers.eq(NotificationLevel.INFO)
        );

        assertThat(eventIdCaptor.getAllValues().get(0))
                .isNotNull();

        assertThat(eventIdCaptor.getAllValues().get(1))
                .isEqualTo(
                        eventIdCaptor.getAllValues().get(0)
                );
    }

    @Test
    @DisplayName("동일 eventId 동시 저장 충돌이 발생하면 알림 생성을 한 번 재시도한다")
    void consume_retriesOnceWhenConcurrentDuplicateEventOccurs() throws Exception {

        // given
        UUID eventId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        NotificationCreatedMessage message =
                new NotificationCreatedMessage(
                        eventId,
                        receiverId,
                        "알림 제목",
                        "알림 내용",
                        NotificationLevel.INFO,
                        Instant.parse("2026-08-20T01:00:00Z")
                );

        String payload = objectMapper.writeValueAsString(message);

        ConsumerRecord<String, String> record =
                new ConsumerRecord<>(
                        NotificationKafkaTopics.NOTIFICATION_CREATED,
                        1,
                        10L,
                        null,
                        payload
                );

        given(notificationService.createNotification(
                eventId,
                receiverId,
                "알림 제목",
                "알림 내용",
                NotificationLevel.INFO
        ))
                .willThrow(new DataIntegrityViolationException(
                        "duplicate eventId"
                ))
                .willReturn(null);

        // when
        notificationKafkaConsumer.consume(record);

        // then
        verify(notificationService, times(2))
                .createNotification(
                        eventId,
                        receiverId,
                        "알림 제목",
                        "알림 내용",
                        NotificationLevel.INFO
                );
    }
}