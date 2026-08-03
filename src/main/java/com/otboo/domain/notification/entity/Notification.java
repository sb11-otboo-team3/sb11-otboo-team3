package com.otboo.domain.notification.entity;

import com.otboo.domain.user.entity.User;
import com.otboo.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "receiver_id", nullable = false)
  private User receiver;

  @Column(name = "title", nullable = false, length = 255)
  private String title;

  @Column(name = "content", nullable = false, columnDefinition = "TEXT")
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(name = "level", nullable = false, length = 20)
  private NotificationLevel level;

  private Notification(
      User receiver,
      String title,
      String content,
      NotificationLevel level) {
    this.receiver = receiver;
    this.title = title;
    this.content = content;
    this.level = level;
  }

  public static Notification create(
      User user,
      String title,
      String content,
      NotificationLevel level) {
    return new Notification(user, title, content, level);
  }
}
