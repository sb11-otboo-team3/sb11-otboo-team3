package com.otboo.domain.notification.repository;

import com.otboo.domain.notification.entity.Notification;
import java.util.List;
import java.util.UUID;

public interface NotificationRepositoryCustom {

  List<Notification> findNotifications(
      UUID receiverId,
      String cursor,
      UUID idAfter,
      int limit
  );

  long countNotifications(UUID receiverId);
}
