package com.otboo.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.notification.entity.Notification;
import com.otboo.domain.notification.entity.NotificationLevel;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.global.config.JpaAuditingConfig;
import com.otboo.global.config.QuerydslConfig;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import jakarta.persistence.EntityManager;

@DataJpaTest
@Import({QuerydslConfig.class, JpaAuditingConfig.class})
class NotificationRepositoryTest {

  @Autowired
  private NotificationRepository notificationRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private EntityManager entityManager;

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

  @Test
  @DisplayName("알림 목록 조회 시 커서 이후 알림만 조회한다")
  void findNotifications_withCursor_success() {
    User receiver = userRepository.save(User.create(
        "receiver_cursor@test.com",
        "receiver",
        "password"
    ));

    Notification first = notificationRepository.save(Notification.create(
        receiver,
        "first",
        "content",
        NotificationLevel.INFO
    ));
    Notification second = notificationRepository.save(Notification.create(
        receiver,
        "second",
        "content",
        NotificationLevel.INFO
    ));

    entityManager.flush();

    Instant sameCreatedAt = Instant.parse("2026-08-13T01:00:00Z");
    updateCreatedAt(List.of(first.getId(), second.getId()), sameCreatedAt);

    entityManager.clear();

    List<Notification> allNotifications = notificationRepository.findNotifications(
        receiver.getId(),
        null,
        null,
        10
    );

    Notification cursorNotification = allNotifications.get(0);

    List<Notification> result = notificationRepository.findNotifications(
        receiver.getId(),
        cursorNotification.getCreatedAt().toString(),
        cursorNotification.getId(),
        10
    );

    assertThat(allNotifications).hasSize(2);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getId()).isEqualTo(allNotifications.get(1).getId());
  }

  @Test
  @DisplayName("lastEventId 이후 알림을 조회한다")
  void findNotificationsAfter_success() {
    User receiver = userRepository.save(User.create(
        "receiver_after@test.com",
        "receiver",
        "password"
    ));

    Notification first = notificationRepository.save(Notification.create(
        receiver,
        "first",
        "content",
        NotificationLevel.INFO
    ));
    Notification second = notificationRepository.save(Notification.create(
        receiver,
        "second",
        "content",
        NotificationLevel.INFO
    ));

    entityManager.flush();

    updateCreatedAt(first.getId(), Instant.parse("2026-08-13T01:00:00Z"));
    updateCreatedAt(second.getId(), Instant.parse("2026-08-13T02:00:00Z"));

    entityManager.clear();

    List<Notification> result = notificationRepository.findNotificationsAfter(
        receiver.getId(),
        first.getId(),
        10
    );

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getId()).isEqualTo(second.getId());
  }

  private void updateCreatedAt(UUID id, Instant createdAt) {
    updateCreatedAt(List.of(id), createdAt);
  }

  private void updateCreatedAt(List<UUID> ids, Instant createdAt) {
    entityManager.createQuery("""
          update Notification notification
          set notification.createdAt = :createdAt
          where notification.id in :ids
          """)
        .setParameter("createdAt", createdAt)
        .setParameter("ids", ids)
        .executeUpdate();

    entityManager.flush();
  }
}