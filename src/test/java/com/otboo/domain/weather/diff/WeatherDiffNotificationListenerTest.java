package com.otboo.domain.weather.diff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.otboo.domain.notification.entity.NotificationLevel;
import com.otboo.domain.notification.event.NotificationEvent;
import com.otboo.domain.profile.entity.Profile;
import com.otboo.domain.profile.repository.ProfileRepository;
import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.entity.Weather;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class WeatherDiffNotificationListenerTest {

  @Mock
  private ProfileRepository profileRepository;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  // 순수 로직이라(의존성 없음) 실제 인스턴스를 씀 - 이 테스트가 진짜 문구까지 검증하게 하려고.
  private final WeatherDiffMessageBuilder messageBuilder = new WeatherDiffMessageBuilder();

  private WeatherDiffNotificationListener listener;

  private final Grid grid = Grid.builder().x(60).y(127).build();

  @BeforeEach
  void setUp() {
    listener = new WeatherDiffNotificationListener(profileRepository, messageBuilder, eventPublisher);
  }

  private Weather weather(double temperature, PrecipitationType precipitationType, double windSpeed) {
    Instant now = Instant.parse("2026-07-30T00:00:00Z");
    return Weather.builder()
        .grid(grid)
        .forecastedAt(now)
        .forecastAt(now)
        .skyStatus(SkyStatus.CLEAR)
        .precipitationType(precipitationType)
        .precipitationProbability(10.0)
        .temperatureCurrent(temperature)
        .windSpeed(windSpeed)
        .build();
  }

  private Profile mockProfile(UUID userId) {
    Profile profile = Mockito.mock(Profile.class);
    given(profile.getUserId()).willReturn(userId);
    return profile;
  }

  @Test
  @DisplayName("발표별 급변 이벤트를 받으면 같은 격자의 유저 각각에게 알림 이벤트를 발행한다")
  void publishesNotificationForEachProfileOnAnnouncementDiff() {
    // given
    UUID userId1 = UUID.randomUUID();
    UUID userId2 = UUID.randomUUID();
    Profile profile1 = mockProfile(userId1);
    Profile profile2 = mockProfile(userId2);
    given(profileRepository.findByXAndY(grid.getX(), grid.getY())).willReturn(List.of(profile1, profile2));

    Weather previous = weather(20.0, PrecipitationType.NONE, 2.0);
    Weather current = weather(26.0, PrecipitationType.NONE, 2.0);
    WeatherAnnouncementDiffEvent event =
        new WeatherAnnouncementDiffEvent(previous, current, EnumSet.of(DiffCategory.TEMPERATURE));

    // when
    listener.handle(event);

    // then
    ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
    verify(eventPublisher, times(2)).publishEvent(captor.capture());
    List<NotificationEvent> published = captor.getAllValues();
    assertThat(published)
        .extracting(NotificationEvent::receiverId)
        .containsExactlyInAnyOrder(userId1, userId2);
    assertThat(published)
        .allSatisfy(notification -> {
          assertThat(notification.content())
              .isEqualTo("9시 기온 예보가 20.0°C에서 26.0°C로 상향 조정됐어요");
          assertThat(notification.level()).isEqualTo(NotificationLevel.WARNING);
        });
  }

  @Test
  @DisplayName("발표별 급변이라도 같은 격자에 유저가 없으면 알림을 발행하지 않는다")
  void publishesNothingWhenNoProfilesForAnnouncementDiff() {
    // given
    given(profileRepository.findByXAndY(grid.getX(), grid.getY())).willReturn(List.of());
    Weather previous = weather(20.0, PrecipitationType.NONE, 2.0);
    Weather current = weather(26.0, PrecipitationType.NONE, 2.0);
    WeatherAnnouncementDiffEvent event =
        new WeatherAnnouncementDiffEvent(previous, current, EnumSet.of(DiffCategory.TEMPERATURE));

    // when
    listener.handle(event);

    // then
    Mockito.verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("일일별 급변 이벤트를 받으면 같은 격자의 유저 각각에게 알림 이벤트를 발행한다")
  void publishesNotificationForEachProfileOnDailyDiff() {
    // given
    UUID userId = UUID.randomUUID();
    Profile profile = mockProfile(userId);
    given(profileRepository.findByXAndY(grid.getX(), grid.getY())).willReturn(List.of(profile));

    DailyDiffTrigger trigger = new DailyDiffTrigger(
        DiffCategory.WIND, Instant.parse("2026-07-30T06:00:00Z"), true);
    WeatherDailyDiffEvent event = new WeatherDailyDiffEvent(grid, LocalDate.of(2026, 7, 30), Set.of(trigger));

    // when
    listener.handle(event);

    // then
    ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().receiverId()).isEqualTo(userId);
    assertThat(captor.getValue().content())
        .isEqualTo("15시부터 바람이 강해질 것 같아요");
  }

  @Test
  @DisplayName("일일별 급변이라도 같은 격자에 유저가 없으면 알림을 발행하지 않는다")
  void publishesNothingWhenNoProfilesForDailyDiff() {
    // given
    given(profileRepository.findByXAndY(grid.getX(), grid.getY())).willReturn(List.of());
    DailyDiffTrigger trigger = new DailyDiffTrigger(
        DiffCategory.WIND, Instant.parse("2026-07-30T06:00:00Z"), true);
    WeatherDailyDiffEvent event = new WeatherDailyDiffEvent(grid, LocalDate.of(2026, 7, 30), Set.of(trigger));

    // when
    listener.handle(event);

    // then
    Mockito.verifyNoInteractions(eventPublisher);
  }
}
