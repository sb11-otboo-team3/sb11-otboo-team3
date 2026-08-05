package com.otboo.domain.notification.mapper;

import com.otboo.domain.notification.dto.response.NotificationDto;
import com.otboo.domain.notification.entity.Notification;

public final class NotificationMapper {

  private NotificationMapper() {
  }

  public static NotificationDto toDto(Notification notification) {
    return new NotificationDto(
        notification.getId(),
        notification.getCreatedAt(),
        notification.getReceiver().getId(),
        notification.getTitle(),
        notification.getContent(),
        notification.getLevel()
    );
  }
}