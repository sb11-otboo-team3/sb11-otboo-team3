package com.otboo.domain.notification.outbox;

public enum NotificationOutboxStatus {
  PENDING,
  PUBLISHED,
  FAILED  // 재시도 횟수 넘김
}