package com.otboo.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.otboo.domain.weather.diff.DiffCategory;
import com.otboo.domain.weather.diff.WeatherAnnouncementDiffEvent;
import com.otboo.domain.weather.diff.WeatherDiffEvaluator;
import com.otboo.domain.weather.diff.WeatherDiffProperties;
import com.otboo.domain.weather.dto.VilageFcstItem;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.dto.WeatherDto;
import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.entity.Weather;
import com.otboo.domain.weather.repository.WeatherRepository;
import com.otboo.domain.weather.util.VilageFcstBaseTimeResolver;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
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
class WeatherPersisterTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @Mock
  private WeatherRepository weatherRepository;

  @Mock
  private WeatherSaver weatherSaver;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  // 순수 로직이라(의존성 없음) 목이 아니라 실제 인스턴스를 씀 - 이 테스트가 진짜 임계값 계산까지 검증하게 하려고.
  private final WeatherDiffEvaluator weatherDiffEvaluator = new WeatherDiffEvaluator();
  private final VilageFcstBaseTimeResolver baseTimeResolver = new VilageFcstBaseTimeResolver();
  private final WeatherDiffProperties weatherDiffProperties = new WeatherDiffProperties(5.0, 3.0);

  private WeatherPersister weatherPersister;

  private final Grid grid = Grid.builder().x(60).y(127).build();
  private final WeatherAPILocation location = new WeatherAPILocation(37.5665, 126.9780, 60, 127,
      List.of("서울특별시", "강서구", "마곡동"));

  @BeforeEach
  void setUp() {
    weatherPersister = new WeatherPersister(
        weatherRepository, weatherSaver, weatherDiffEvaluator, baseTimeResolver, weatherDiffProperties, eventPublisher);
  }

  private VilageFcstItem item(LocalDateTime forecastAt, SkyStatus skyStatus, PrecipitationType precipitationType) {
    return new VilageFcstItem(
        LocalDateTime.of(2026, 7, 30, 5, 0),
        forecastAt,
        skyStatus,
        precipitationType,
        0.0,
        20.0,
        55.0,
        23.0,
        null,
        null,
        2.3
    );
  }

  // dayBefore/previousAnnouncement 조회가 findByGridAndForecastAtIn 하나로 합쳐졌으니(WeatherPersister
  // 참고), 두 시각 중 실제로 존재하는 row만 담아 스텁하면 된다.
  private void stubExisting(Instant dayBeforeForecastAt, Instant forecastAt, Weather... existing) {
    given(weatherRepository.findByGridAndForecastAtIn(grid, List.of(dayBeforeForecastAt, forecastAt)))
        .willReturn(List.of(existing));
  }

  @Test
  @DisplayName("전날 같은 시각 기록이 없으면 전일 대비 값 없이 저장한다")
  void savesWithoutComparedToDayBeforeWhenNoYesterdayRecord() {
    // given
    VilageFcstItem item = item(LocalDateTime.of(2026, 7, 30, 9, 0), SkyStatus.CLEAR, PrecipitationType.NONE);
    Instant forecastAt = LocalDateTime.of(2026, 7, 30, 9, 0).atZone(KST).toInstant();
    Instant dayBeforeForecastAt = LocalDateTime.of(2026, 7, 29, 9, 0).atZone(KST).toInstant();
    stubExisting(dayBeforeForecastAt, forecastAt);
    given(weatherSaver.upsertInNewTransaction(any(Weather.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    // when
    Optional<WeatherDto> result = weatherPersister.persist(item, grid, location);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().humidity().comparedToDayBefore()).isEqualTo(0.0);
    assertThat(result.get().temperature().comparedToDayBefore()).isEqualTo(0.0);

    ArgumentCaptor<Weather> captor = ArgumentCaptor.forClass(Weather.class);
    verify(weatherSaver).upsertInNewTransaction(captor.capture());
    assertThat(captor.getValue().getHumidityComparedToDayBefore()).isNull();
    assertThat(captor.getValue().getTemperatureComparedToDayBefore()).isNull();
  }

  @Test
  @DisplayName("하루 전 같은 시각 기록이 있으면 전일 대비 값을 계산해서 저장한다")
  void computesComparedToDayBeforeWhenYesterdayRecordExists() {
    // given
    VilageFcstItem item = item(LocalDateTime.of(2026, 7, 30, 9, 0), SkyStatus.CLEAR, PrecipitationType.NONE);
    Instant forecastAt = LocalDateTime.of(2026, 7, 30, 9, 0).atZone(KST).toInstant();
    Instant dayBeforeForecastAt = LocalDateTime.of(2026, 7, 29, 9, 0).atZone(KST).toInstant();
    Weather yesterday = Weather.builder()
        .grid(grid)
        .forecastedAt(dayBeforeForecastAt)
        .forecastAt(dayBeforeForecastAt)
        .skyStatus(SkyStatus.CLOUDY)
        .precipitationType(PrecipitationType.NONE)
        .humidityCurrent(50.0)
        .temperatureCurrent(20.0)
        .build();
    stubExisting(dayBeforeForecastAt, forecastAt, yesterday);
    given(weatherSaver.upsertInNewTransaction(any(Weather.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    // when
    Optional<WeatherDto> result = weatherPersister.persist(item, grid, location);

    // then
    ArgumentCaptor<Weather> captor = ArgumentCaptor.forClass(Weather.class);
    verify(weatherSaver).upsertInNewTransaction(captor.capture());
    assertThat(captor.getValue().getHumidityComparedToDayBefore()).isEqualTo(5.0);
    assertThat(captor.getValue().getTemperatureComparedToDayBefore()).isEqualTo(3.0);

    assertThat(result).isPresent();
    assertThat(result.get().humidity().comparedToDayBefore()).isEqualTo(5.0);
    assertThat(result.get().temperature().comparedToDayBefore()).isEqualTo(3.0);
  }

  @Test
  @DisplayName("기상청이 매핑 안 되는 하늘상태/강수형태 코드를 내려주면 저장하지 않고 건너뛴다")
  void skipsItemWithUnsupportedSkyStatusOrPrecipitationType() {
    // given: 매핑 안 되는 코드는 VilageFcstItem 생성 단계에서 이미 null로 들어옴
    VilageFcstItem unsupportedSky = item(LocalDateTime.of(2026, 7, 30, 9, 0), null, PrecipitationType.NONE);
    VilageFcstItem unsupportedPrecipitation = item(LocalDateTime.of(2026, 7, 30, 9, 0), SkyStatus.CLEAR, null);

    // when
    Optional<WeatherDto> resultForSky = weatherPersister.persist(unsupportedSky, grid, location);
    Optional<WeatherDto> resultForPrecipitation = weatherPersister.persist(unsupportedPrecipitation, grid, location);

    // then
    assertThat(resultForSky).isEmpty();
    assertThat(resultForPrecipitation).isEmpty();
    Mockito.verifyNoInteractions(weatherSaver);
  }

  @Test
  @DisplayName("이미 같은 시간대에 row가 있으면, upsert가 덮어쓴 결과를 그대로 응답으로 쓴다")
  void returnsUpsertedRowWhenSlotAlreadyExists() {
    // given: 예전 배치가 이미 이 시간대를 CLOUDY로 저장해뒀고, 이번 배치는 CLEAR로 다시 예측하는 상황.
    // upsert가 그 기존 row를 덮어쓴 결과(응답 DTO 기준으론 CLOUDY가 아니라 최신값)를 리턴한다고 가정하면,
    // persist()가 그 리턴값을 그대로 쓰는지(직접 새로 만든 Weather를 쓰는 게 아니라) 확인할 수 있다.
    VilageFcstItem item = item(LocalDateTime.of(2026, 7, 30, 9, 0), SkyStatus.CLEAR, PrecipitationType.NONE);
    Instant forecastAt = LocalDateTime.of(2026, 7, 30, 9, 0).atZone(KST).toInstant();
    Instant dayBeforeForecastAt = LocalDateTime.of(2026, 7, 29, 9, 0).atZone(KST).toInstant();
    stubExisting(dayBeforeForecastAt, forecastAt);

    Instant forecastedAt = LocalDateTime.of(2026, 7, 30, 5, 0).atZone(KST).toInstant();
    Weather upserted = Weather.builder()
        .grid(grid)
        .forecastedAt(forecastedAt)
        .forecastAt(forecastAt)
        .skyStatus(SkyStatus.MOSTLY_CLOUDY)
        .precipitationType(PrecipitationType.NONE)
        .build();
    given(weatherSaver.upsertInNewTransaction(any(Weather.class))).willReturn(upserted);

    // when
    Optional<WeatherDto> result = weatherPersister.persist(item, grid, location);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().skyStatus()).isEqualTo(SkyStatus.MOSTLY_CLOUDY);
  }

  @Test
  @DisplayName("DB가 계산해준 min/max가 있으면 그대로 감싸서 리턴한다")
  void resolveDailyMinMaxReturnsValueWhenPresent() {
    // given
    LocalDate date = LocalDate.of(2026, 7, 30);
    Instant dayStart = date.atStartOfDay(KST).toInstant();
    Instant dayEnd = date.plusDays(1).atStartOfDay(KST).toInstant();
    WeatherRepository.DailyTemperatureRangeProjection projection =
        Mockito.mock(WeatherRepository.DailyTemperatureRangeProjection.class);
    given(projection.getResolvedMin()).willReturn(18.0);
    given(projection.getResolvedMax()).willReturn(27.0);
    given(weatherRepository.findDailyTemperatureRange(grid.getId(), dayStart, dayEnd))
        .willReturn(projection);

    // when
    Optional<WeatherPersister.DailyTemperatureRange> result = weatherPersister.resolveDailyMinMax(grid, date);

    // then
    assertThat(result).contains(new WeatherPersister.DailyTemperatureRange(18.0, 27.0));
  }

  @Test
  @DisplayName("이 날짜에 유효한 기온 데이터가 하나도 없으면(min/max가 null) 빈 값을 리턴한다")
  void resolveDailyMinMaxReturnsEmptyWhenNoValidData() {
    // given
    LocalDate date = LocalDate.of(2026, 7, 30);
    Instant dayStart = date.atStartOfDay(KST).toInstant();
    Instant dayEnd = date.plusDays(1).atStartOfDay(KST).toInstant();
    // getResolvedMin()이 null이면 short-circuit으로 getResolvedMax()는 아예 호출되지 않으므로 스텁 안 함.
    WeatherRepository.DailyTemperatureRangeProjection projection =
        Mockito.mock(WeatherRepository.DailyTemperatureRangeProjection.class);
    given(projection.getResolvedMin()).willReturn(null);
    given(weatherRepository.findDailyTemperatureRange(grid.getId(), dayStart, dayEnd))
        .willReturn(projection);

    // when
    Optional<WeatherPersister.DailyTemperatureRange> result = weatherPersister.resolveDailyMinMax(grid, date);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("위치 정보 없이도(배치용) 저장에 성공하면 저장된 엔티티를 그대로 리턴한다")
  void persistWithoutLocationReturnsSavedEntityWhenSuccessful() {
    // given: 배치는 응답 DTO(위치 포함)가 필요 없으니 location 없는 오버로드를 쓴다
    VilageFcstItem item = item(LocalDateTime.of(2026, 7, 30, 9, 0), SkyStatus.CLEAR, PrecipitationType.NONE);
    Instant forecastAt = LocalDateTime.of(2026, 7, 30, 9, 0).atZone(KST).toInstant();
    Instant dayBeforeForecastAt = LocalDateTime.of(2026, 7, 29, 9, 0).atZone(KST).toInstant();
    stubExisting(dayBeforeForecastAt, forecastAt);
    given(weatherSaver.upsertInNewTransaction(any(Weather.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    // when
    Optional<Weather> result = weatherPersister.persist(item, grid);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getSkyStatus()).isEqualTo(SkyStatus.CLEAR);
    assertThat(result.get().getTemperatureCurrent()).isEqualTo(23.0);
    verify(weatherSaver).upsertInNewTransaction(any(Weather.class));
  }

  @Test
  @DisplayName("위치 정보 없는 오버로드도 매핑 안 되는 코드는 동일하게 건너뛴다")
  void persistWithoutLocationSkipsUnsupportedCodesLikeLocationOverload() {
    // given
    VilageFcstItem unsupportedSky = item(LocalDateTime.of(2026, 7, 30, 9, 0), null, PrecipitationType.NONE);

    // when
    Optional<Weather> result = weatherPersister.persist(unsupportedSky, grid);

    // then
    assertThat(result).isEmpty();
    Mockito.verifyNoInteractions(weatherSaver);
  }

  @Test
  @DisplayName("persistDailyMinMax는 계산된 값을 그대로 updateDailyTemperatureRange에 넘긴다")
  void persistDailyMinMaxDelegatesToRepository() {
    // given
    LocalDate date = LocalDate.of(2026, 7, 30);
    Instant dayStart = date.atStartOfDay(KST).toInstant();
    Instant dayEnd = date.plusDays(1).atStartOfDay(KST).toInstant();

    // when
    weatherPersister.persistDailyMinMax(grid, date, 18.0, 27.0);

    // then
    verify(weatherRepository).updateDailyTemperatureRange(grid, 18.0, 27.0, dayStart, dayEnd);
  }

  // 발표별 급변 - forecastedAt 05:00 기준 다음 발표는 08:00. forecastAt 07:00은 스코프 안, 09:00은 스코프 밖.
  private VilageFcstItem announcementItem(LocalDateTime forecastAt, PrecipitationType precipitationType,
      double temperature, double windSpeed) {
    return new VilageFcstItem(
        LocalDateTime.of(2026, 7, 30, 5, 0), forecastAt,
        SkyStatus.CLEAR, precipitationType,
        0.0, 20.0, 55.0, temperature, null, null, windSpeed
    );
  }

  private Weather previousSlot(Instant forecastAt, PrecipitationType precipitationType,
      double temperature, double windSpeed) {
    return Weather.builder()
        .grid(grid)
        .forecastedAt(forecastAt)
        .forecastAt(forecastAt)
        .skyStatus(SkyStatus.CLEAR)
        .precipitationType(precipitationType)
        .temperatureCurrent(temperature)
        .windSpeed(windSpeed)
        .build();
  }

  @Test
  @DisplayName("같은 시간대에 이전 발표 기록이 없으면 발표별 급변 이벤트를 발행하지 않는다")
  void doesNotPublishAnnouncementDiffEventWhenNoPreviousRecord() {
    // given: 다음 발표(08시) 전이지만 이전 기록 자체가 없음
    VilageFcstItem item = announcementItem(LocalDateTime.of(2026, 7, 30, 7, 0), PrecipitationType.NONE, 26.0, 2.0);
    Instant forecastAt = LocalDateTime.of(2026, 7, 30, 7, 0).atZone(KST).toInstant();
    Instant dayBeforeForecastAt = LocalDateTime.of(2026, 7, 29, 7, 0).atZone(KST).toInstant();
    stubExisting(dayBeforeForecastAt, forecastAt);
    given(weatherSaver.upsertInNewTransaction(any(Weather.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    // when
    weatherPersister.persist(item, grid);

    // then
    Mockito.verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("다음 발표 전 시간대가 아니면 이전 기록이 있어도 발표별 급변을 평가하지 않는다")
  void doesNotPublishAnnouncementDiffEventWhenOutsideNextAnnouncementWindow() {
    // given: forecastAt 09시는 다음 발표(08시) 이후라 스코프 밖. 기온은 5도→26도로 크게 다름에도 무시돼야 함.
    VilageFcstItem item = announcementItem(LocalDateTime.of(2026, 7, 30, 9, 0), PrecipitationType.NONE, 26.0, 2.0);
    Instant forecastAt = LocalDateTime.of(2026, 7, 30, 9, 0).atZone(KST).toInstant();
    Instant dayBeforeForecastAt = LocalDateTime.of(2026, 7, 29, 9, 0).atZone(KST).toInstant();
    stubExisting(dayBeforeForecastAt, forecastAt, previousSlot(forecastAt, PrecipitationType.NONE, 5.0, 2.0));
    given(weatherSaver.upsertInNewTransaction(any(Weather.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    // when
    weatherPersister.persist(item, grid);

    // then
    Mockito.verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("스코프 안에서 기온만 임계값 이상 바뀌면 TEMPERATURE만 담아 이벤트를 발행한다")
  void publishesAnnouncementDiffEventWithTemperatureOnly() {
    // given: 20도 -> 26도(Δ6 ≥ 5), 강수·풍속은 그대로
    VilageFcstItem item = announcementItem(LocalDateTime.of(2026, 7, 30, 7, 0), PrecipitationType.NONE, 26.0, 2.0);
    Instant forecastAt = LocalDateTime.of(2026, 7, 30, 7, 0).atZone(KST).toInstant();
    Instant dayBeforeForecastAt = LocalDateTime.of(2026, 7, 29, 7, 0).atZone(KST).toInstant();
    stubExisting(dayBeforeForecastAt, forecastAt, previousSlot(forecastAt, PrecipitationType.NONE, 20.0, 2.0));
    given(weatherSaver.upsertInNewTransaction(any(Weather.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    // when
    weatherPersister.persist(item, grid);

    // then
    ArgumentCaptor<WeatherAnnouncementDiffEvent> captor = ArgumentCaptor.forClass(WeatherAnnouncementDiffEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().triggeredCategories()).containsExactly(DiffCategory.TEMPERATURE);
  }

  @Test
  @DisplayName("스코프 안에서 강수형태가 NONE에서 강수로 전환되면 PRECIPITATION만 담아 이벤트를 발행한다")
  void publishesAnnouncementDiffEventWithPrecipitationOnly() {
    // given: 기온·풍속은 그대로, 강수형태만 NONE -> RAIN
    VilageFcstItem item = announcementItem(LocalDateTime.of(2026, 7, 30, 7, 0), PrecipitationType.RAIN, 20.0, 2.0);
    Instant forecastAt = LocalDateTime.of(2026, 7, 30, 7, 0).atZone(KST).toInstant();
    Instant dayBeforeForecastAt = LocalDateTime.of(2026, 7, 29, 7, 0).atZone(KST).toInstant();
    stubExisting(dayBeforeForecastAt, forecastAt, previousSlot(forecastAt, PrecipitationType.NONE, 20.0, 2.0));
    given(weatherSaver.upsertInNewTransaction(any(Weather.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    // when
    weatherPersister.persist(item, grid);

    // then
    ArgumentCaptor<WeatherAnnouncementDiffEvent> captor = ArgumentCaptor.forClass(WeatherAnnouncementDiffEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().triggeredCategories()).containsExactly(DiffCategory.PRECIPITATION);
  }

  @Test
  @DisplayName("스코프 안에서 풍속 등급이 오르면 WIND만 담아 이벤트를 발행한다")
  void publishesAnnouncementDiffEventWithWindOnly() {
    // given: 기온·강수는 그대로, 풍속만 2.0(WEAK) -> 10.0(STRONG)
    VilageFcstItem item = announcementItem(LocalDateTime.of(2026, 7, 30, 7, 0), PrecipitationType.NONE, 20.0, 10.0);
    Instant forecastAt = LocalDateTime.of(2026, 7, 30, 7, 0).atZone(KST).toInstant();
    Instant dayBeforeForecastAt = LocalDateTime.of(2026, 7, 29, 7, 0).atZone(KST).toInstant();
    stubExisting(dayBeforeForecastAt, forecastAt, previousSlot(forecastAt, PrecipitationType.NONE, 20.0, 2.0));
    given(weatherSaver.upsertInNewTransaction(any(Weather.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    // when
    weatherPersister.persist(item, grid);

    // then
    ArgumentCaptor<WeatherAnnouncementDiffEvent> captor = ArgumentCaptor.forClass(WeatherAnnouncementDiffEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().triggeredCategories()).containsExactly(DiffCategory.WIND);
  }

  @Test
  @DisplayName("스코프 안이라도 아무 카테고리도 안 걸리면 이벤트를 발행하지 않는다")
  void doesNotPublishAnnouncementDiffEventWhenNothingTriggered() {
    // given: 기온 Δ1(임계값 미만), 강수·풍속 변화 없음
    VilageFcstItem item = announcementItem(LocalDateTime.of(2026, 7, 30, 7, 0), PrecipitationType.NONE, 21.0, 2.0);
    Instant forecastAt = LocalDateTime.of(2026, 7, 30, 7, 0).atZone(KST).toInstant();
    Instant dayBeforeForecastAt = LocalDateTime.of(2026, 7, 29, 7, 0).atZone(KST).toInstant();
    stubExisting(dayBeforeForecastAt, forecastAt, previousSlot(forecastAt, PrecipitationType.NONE, 20.0, 2.0));
    given(weatherSaver.upsertInNewTransaction(any(Weather.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    // when
    weatherPersister.persist(item, grid);

    // then
    Mockito.verifyNoInteractions(eventPublisher);
  }
}
