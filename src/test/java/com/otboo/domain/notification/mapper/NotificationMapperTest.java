package com.otboo.domain.notification.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.notification.dto.response.NotificationDto;
import com.otboo.domain.notification.entity.Notification;
import com.otboo.domain.notification.entity.NotificationLevel;
import com.otboo.domain.user.entity.User;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationMapperTest {

  @Test
  @DisplayName("알림 DTO 변환 성공")
  void toDto_success() {
    UUID notificationId = UUID.randomUUID();
    UUID receiverId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-08-13T01:00:00Z");

    User receiver = User.create("receiver@test.com", "receiver", "password");
    ReflectionTestUtils.setField(receiver, "id", receiverId);

    Notification notification = Notification.create(
        receiver,
        "알림 제목",
        "알림 내용",
        NotificationLevel.INFO
    );
    ReflectionTestUtils.setField(notification, "id", notificationId);
    ReflectionTestUtils.setField(notification, "createdAt", createdAt);

    NotificationDto result = NotificationMapper.toDto(notification);

    assertThat(result.id()).isEqualTo(notificationId);
    assertThat(result.createdAt()).isEqualTo(createdAt);
    assertThat(result.receiverId()).isEqualTo(receiverId);
    assertThat(result.title()).isEqualTo("알림 제목");
    assertThat(result.content()).isEqualTo("알림 내용");
    assertThat(result.level()).isEqualTo(NotificationLevel.INFO);
  }
}