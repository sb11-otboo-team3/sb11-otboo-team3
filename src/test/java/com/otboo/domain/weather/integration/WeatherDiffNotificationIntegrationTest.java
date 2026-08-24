package com.otboo.domain.weather.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.notification.entity.Notification;
import com.otboo.domain.notification.kafka.NotificationKafkaTopics;
import com.otboo.domain.notification.outbox.NotificationOutboxPublisher;
import com.otboo.domain.notification.repository.NotificationRepository;
import com.otboo.domain.profile.entity.Profile;
import com.otboo.domain.profile.repository.ProfileRepository;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.domain.weather.diff.DiffCategory;
import com.otboo.domain.weather.diff.WeatherAnnouncementDiffEvent;
import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.entity.Weather;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 급변 감지 이벤트 하나가 실제로 WeatherDiffNotificationListener -> NotificationEvent ->
// NotificationEventListener(Outbox 저장) -> NotificationOutboxPublisher(Kafka 발행) ->
// NotificationKafkaConsumer(구독) -> NotificationService(Notification 저장)까지 전부 실동작하는지 확인한다.
// 실제 스케줄(3초 주기)을 기다리는 대신 publishPending()을 직접 호출해서 테스트를 빠르고 결정적으로 만든다.
@SpringBootTest(properties = {
    "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
    "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
    "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
    "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
    "spring.kafka.consumer.auto-offset-reset=earliest"
})
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = NotificationKafkaTopics.NOTIFICATION_CREATED, kraft = true)
@DirtiesContext
class WeatherDiffNotificationIntegrationTest {

  @Autowired
  private ApplicationEventPublisher eventPublisher;

  @Autowired
  private NotificationOutboxPublisher notificationOutboxPublisher;

  @Autowired
  private NotificationRepository notificationRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private ProfileRepository profileRepository;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  // 이 스케줄은 원래 shedlock 분산락을 쓰지만(NotificationOutboxPublisher), test 프로필은
  // flyway가 꺼져 있어(application-test.yaml) shedlock 테이블이 안 생긴다 - 락 자체를 검증하는
  // 테스트가 아니므로 실제 마이그레이션(V6__create_shedlock_table.sql)과 같은 스키마로 직접 만든다.
  @BeforeEach
  void createShedlockTable() {
    jdbcTemplate.execute("""
        CREATE TABLE IF NOT EXISTS shedlock (
            name       VARCHAR(64) NOT NULL PRIMARY KEY,
            lock_until TIMESTAMP   NOT NULL,
            locked_at  TIMESTAMP   NOT NULL,
            locked_by  VARCHAR(255) NOT NULL
        )
        """);
  }

  @Test
  @DisplayName("발표별 급변 이벤트가 Outbox와 Kafka를 거쳐 실제 Notification까지 저장된다")
  void announcementDiffEventReachesNotificationThroughKafka() throws InterruptedException {
    // given: 격자(60,127)에 사는 유저 하나
    // user 저장과 profile 저장을 같은 트랜잭션(TransactionTemplate)으로 묶는다 - 따로 커밋하면
    // profile의 @MapsId user 참조가 detached 상태가 되어 profile 저장 시 예외가 난다
    // (ProfileRepositoryTest와 동일한 이유로 회피).
    TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
    User user = txTemplate.execute(status -> {
      User newUser = User.create("kafka-e2e-" + UUID.randomUUID() + "@otboo.io", "카프카E2E테스트", "encoded-password");
      userRepository.saveAndFlush(newUser);
      Profile profile = Profile.createDefault(newUser);
      profile.update(null, null, null, null, 60, 127, null, null, null, null);
      profileRepository.saveAndFlush(profile);
      return newUser;
    });

    Grid grid = Grid.builder().x(60).y(127).build();
    Weather previous = weather(grid, 20.0);
    Weather current = weather(grid, 26.0);
    WeatherAnnouncementDiffEvent event =
        new WeatherAnnouncementDiffEvent(previous, current, EnumSet.of(DiffCategory.TEMPERATURE));

    // when: 급변 이벤트 발행 -> (동기) NotificationEvent 발행 -> Outbox 저장
    eventPublisher.publishEvent(event);

    // 스케줄(3초)을 기다리지 않고 직접 발행 트리거 -> Kafka 전송 -> 실제 컨슈머가 비동기로 소비
    notificationOutboxPublisher.publishPending();

    Notification saved = awaitNotification(user.getId());

    // then
    assertThat(saved.getTitle()).isEqualTo("날씨가 급변할 예정이에요");
    assertThat(saved.getContent()).isEqualTo("09시 기온 예보가 20.0°C에서 26.0°C로 상향 조정됐어요.");
  }

  private Weather weather(Grid grid, double temperature) {
    Instant now = Instant.parse("2026-08-21T00:00:00Z");
    return Weather.builder()
        .grid(grid)
        .forecastedAt(now)
        .forecastAt(now)
        .skyStatus(SkyStatus.CLEAR)
        .precipitationType(PrecipitationType.NONE)
        .precipitationProbability(10.0)
        .temperatureCurrent(temperature)
        .windSpeed(2.0)
        .build();
  }

  // 컨슈머가 비동기로 동작하므로 폴링으로 기다린다(Awaitility 미사용 - 이 프로젝트 의존성에 없음).
  // 20초인 이유: 이 테스트 단독으로는 2초면 끝나지만, 전체 스위트(Testcontainers+여러 EmbeddedKafka가
  // 앞뒤로 뜨고 내려가는 상황)와 같이 돌면 리소스 경합으로 10초를 넘기는 게 관찰돼 여유를 뒀다.
  private Notification awaitNotification(UUID receiverId) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 20_000;
    while (System.currentTimeMillis() < deadline) {
      List<Notification> found = notificationRepository.findAll().stream()
          .filter(notification -> notification.getReceiver().getId().equals(receiverId))
          .toList();
      if (!found.isEmpty()) {
        return found.get(0);
      }
      Thread.sleep(200);
    }
    throw new AssertionError("20초 안에 Kafka를 거쳐 Notification이 저장되지 않았다");
  }
}
