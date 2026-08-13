package com.otboo.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationTest {

  @Test
  @DisplayName("알림 생성 성공")
  void create_success() {
    User receiver = User.create("receiver@test.com", "receiver", "password");

    Notification notification = Notification.create(
        receiver,
        "알림 제목",
        "알림 내용",
        NotificationLevel.INFO
    );

    assertThat(notification.getReceiver()).isEqualTo(receiver);
    assertThat(notification.getTitle()).isEqualTo("알림 제목");
    assertThat(notification.getContent()).isEqualTo("알림 내용");
    assertThat(notification.getLevel()).isEqualTo(NotificationLevel.INFO);
  }

  @Test
  @DisplayName("알림 레벨 enum 확인")
  void notificationLevel_success() {
    assertThat(NotificationLevel.valueOf("INFO")).isEqualTo(NotificationLevel.INFO);
    assertThat(NotificationLevel.valueOf("WARNING")).isEqualTo(NotificationLevel.WARNING);
    assertThat(NotificationLevel.valueOf("ERROR")).isEqualTo(NotificationLevel.ERROR);
  }
}