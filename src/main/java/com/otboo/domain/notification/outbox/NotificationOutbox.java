package com.otboo.domain.notification.outbox;

import com.otboo.domain.notification.entity.NotificationLevel;
import com.otboo.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "notification_outbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationOutbox extends BaseEntity {

  @Column(name = "receiver_id", nullable = false)
  private UUID receiverId;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "content", nullable = false, columnDefinition = "TEXT")
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(name = "level", nullable = false)
  private NotificationLevel level;

  // 알림 발생 시간
  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  // Outbox 처리 상태
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private NotificationOutboxStatus status;

  // Kafka 발행 실패 횟수
  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  // Kafka 발행 성공 시각
  @Column(name = "published_at")
  private Instant publishedAt;

  // Outbox 상태가 마지막으로 변경된 시간
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  private NotificationOutbox(
      UUID receiverId,
      String title,
      String content,
      NotificationLevel level,
      Instant occurredAt
  ) {
    this.receiverId = receiverId;
    this.title = title;
    this.content = content;
    this.level = level;
    this.occurredAt = occurredAt;
    this.status = NotificationOutboxStatus.PENDING;
    this.retryCount = 0;
    this.updatedAt = Instant.now();
  }

  public static NotificationOutbox create(
      UUID receiverId,
      String title,
      String content,
      NotificationLevel level,
      Instant occurredAt
  ) {
    return new NotificationOutbox(receiverId, title, content, level, occurredAt);
  }

  // Kafka 발행 성공
  public void markPublished() {
    this.status = NotificationOutboxStatus.PUBLISHED;
    this.publishedAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  // Kafka 발행 실패
  public void markRetry() {
    this.retryCount++;
    this.updatedAt = Instant.now();
  }

  // Kafka 발행 실패(재시도 한도 넘김)
  public void markFailed() {
    this.retryCount++;
    this.status = NotificationOutboxStatus.FAILED;
    this.updatedAt = Instant.now();
  }
}
