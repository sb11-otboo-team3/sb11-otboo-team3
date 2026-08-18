package com.otboo.domain.notification.kafka;

import com.otboo.domain.notification.entity.NotificationLevel;
import java.time.Instant;
import java.util.UUID;

public record NotificationCreatedMessage(
    UUID receiverId,
    String title,
    String content,
    NotificationLevel level,
    Instant occurredAt
) {

}
