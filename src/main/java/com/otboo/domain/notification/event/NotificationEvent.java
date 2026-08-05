package com.otboo.domain.notification.event;

import com.otboo.domain.notification.entity.NotificationLevel;
import java.util.UUID;

public record NotificationEvent(
    UUID receiverId,
    String title,
    String content,
    NotificationLevel level
) {
}