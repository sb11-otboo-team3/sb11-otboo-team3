package com.otboo.domain.notification.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class NotificationNotFoundException extends NotificationException {

  public NotificationNotFoundException(UUID notificationId) {
    super(HttpStatus.BAD_REQUEST, "알림을 찾을 수 없습니다: " + notificationId);
  }
}