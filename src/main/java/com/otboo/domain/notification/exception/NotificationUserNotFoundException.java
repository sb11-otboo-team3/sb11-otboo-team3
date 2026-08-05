package com.otboo.domain.notification.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class NotificationUserNotFoundException extends NotificationException {

  public NotificationUserNotFoundException(UUID userId) {
    super(HttpStatus.BAD_REQUEST, "알림 수신자를 찾을 수 없습니다: " + userId);
  }
}