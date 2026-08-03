package com.otboo.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.notification.entity.Notification;
import com.otboo.domain.notification.entity.NotificationLevel;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.global.config.JpaAuditingConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class NotificationRepositoryTest {

  @Autowired
  private NotificationRepository notificationRepository;

  @Autowired
  private UserRepository userRepository;

  @Test
  @DisplayName("특정 사용자의 알림만 조회 테스트")
  void findNotifications_byReceiver_success() {
    User receiver = userRepository.save(User.create("receiver@test.com", "receiver", "password"));
    User other = userRepository.save(User.create("other@test.com", "other", "password"));

    Notification notification = Notification.create(
        receiver,
        "내 알림",
        "내용",
        NotificationLevel.INFO
    );
    Notification otherNotification = Notification.create(
        other,
        "다른 사람 알림",
        "내용",
        NotificationLevel.INFO
    );

    notificationRepository.save(notification);
    notificationRepository.save(otherNotification);

    List<Notification> result = notificationRepository.findNotifications(
        receiver.getId(),
        null,
        null,
        20
    );

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getReceiver().getId()).isEqualTo(receiver.getId());
    assertThat(result.get(0).getTitle()).isEqualTo("내 알림");
  }

  @Test
  @DisplayName("특정 사용자의 알림 개수를 조회한다")
  void countNotifications_success() {
    User receiver = userRepository.save(User.create("receiver2@test.com", "receiver", "password"));
    User other = userRepository.save(User.create("other2@test.com", "other", "password"));

    notificationRepository.save(Notification.create(
        receiver,
        "알림 1",
        "내용",
        NotificationLevel.INFO
    ));
    notificationRepository.save(Notification.create(
        receiver,
        "알림 2",
        "내용",
        NotificationLevel.INFO
    ));
    notificationRepository.save(Notification.create(
        other,
        "다른 사람 알림",
        "내용",
        NotificationLevel.INFO
    ));

    long count = notificationRepository.countNotifications(receiver.getId());

    assertThat(count).isEqualTo(2L);
  }
}