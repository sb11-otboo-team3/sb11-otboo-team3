package com.otboo.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.otboo.domain.weather.cache.WeatherForecastCache;
import com.otboo.domain.weather.client.KmaWeatherClient;
import com.otboo.domain.weather.dto.HumidityDto;
import com.otboo.domain.weather.dto.PrecipitationDto;
import com.otboo.domain.weather.dto.TemperatureDto;
import com.otboo.domain.weather.dto.VilageFcstItem;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.dto.WeatherDto;
import com.otboo.domain.weather.dto.WindSpeedDto;
import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.entity.Weather;
import com.otboo.domain.weather.entity.WindStrength;
import com.otboo.domain.weather.exception.KmaApiException;
import com.otboo.domain.weather.repository.GridRepository;
import com.otboo.domain.weather.repository.WeatherRepository;
import com.otboo.domain.weather.util.DailyForecastSelector;
import com.otboo.domain.weather.util.VilageFcstBaseTime;
import com.otboo.domain.weather.util.VilageFcstBaseTimeResolver;
import com.otboo.domain.weather.util.WeatherGrid;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class WeatherServiceImplTest {

  @Mock
  private GridRepository gridRepository;

  @Mock
  private LocationResolver locationResolver;

  @Mock
  private VilageFcstBaseTimeResolver baseTimeResolver;

  @Mock
  private KmaWeatherClient kmaWeatherClient;

  @Mock
  private WeatherRepository weatherRepository;

  @Mock
  private WeatherSaver weatherSaver;

  @Mock
  private WeatherForecastCache weatherForecastCache;

  private final Clock clock = Clock.fixed(
      LocalDateTime.of(2026, 7, 30, 9, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant(),
      ZoneId.of("Asia/Seoul")
  );

  private WeatherServiceImpl weatherService;

  @BeforeEach
  void setUp() {
    weatherService = new WeatherServiceImpl(
        gridRepository,
        locationResolver,
        baseTimeResolver,
        kmaWeatherClient,
        weatherRepository,
        weatherSaver,
        new DailyForecastSelector(),
        clock,
        weatherForecastCache
    );
  }

  private WeatherAPILocation location(double latitude, double longitude) {
    return new WeatherAPILocation(latitude, longitude, 60, 127,
        new String[]{"서울특별시", "강서구", "마곡동"});
  }

  @Test
  @DisplayName("getLocation은 LocationResolver에게 위임한다")
  void getLocationDelegatesToLocationResolver() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    WeatherAPILocation location = location(latitude, longitude);
    given(locationResolver.resolve(latitude, longitude)).willReturn(location);

    // when
    WeatherAPILocation result = weatherService.getLocation(latitude, longitude);

    // then
    assertThat(result).isEqualTo(location);
    verify(locationResolver).resolve(latitude, longitude);
  }

  @Test
  @DisplayName("위경도로 조회하면 위치 정보와 기상청 예보를 조합해 목록을 반환한다")
  void returnsWeatherListCombiningLocationAndForecast() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    WeatherAPILocation location = location(latitude, longitude);
    Grid existingGrid = Grid.builder().x(60).y(127).build();

    given(locationResolver.resolve(latitude, longitude)).willReturn(location);
    given(gridRepository.findByXAndY(60, 127)).willReturn(Optional.of(existingGrid));

    VilageFcstBaseTime baseTime = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(5, 0));
    given(baseTimeResolver.resolve(any())).willReturn(baseTime);

    VilageFcstItem item = new VilageFcstItem(
        LocalDateTime.of(2026, 7, 30, 5, 0),
        LocalDateTime.of(2026, 7, 30, 9, 0),
        SkyStatus.CLEAR,
        PrecipitationType.NONE,
        0.0,
        20.0,
        55.0,
        23.0,
        null,
        null,
        2.3,
        WindStrength.WEAK
    );
    given(kmaWeatherClient.getForecast(60, 127, baseTime)).willReturn(List.of(item));

    // when
    List<WeatherDto> result = weatherService.getWeathers(latitude, longitude);

    // then
    assertThat(result).hasSize(1);
    WeatherDto dto = result.get(0);
    assertThat(dto.location().locationNames()).containsExactly("서울특별시", "강서구", "마곡동");
    assertThat(dto.skyStatus()).isEqualTo(SkyStatus.CLEAR);
    assertThat(dto.precipitation().type()).isEqualTo(PrecipitationType.NONE);
    assertThat(dto.precipitation().amount()).isEqualTo(0.0);
    assertThat(dto.precipitation().probability()).isEqualTo(20.0);
    verify(weatherSaver).saveInNewTransaction(any(Weather.class));
    assertThat(dto.humidity().current()).isEqualTo(55.0);
    assertThat(dto.temperature().current()).isEqualTo(23.0);
    assertThat(dto.windSpeed().speed()).isEqualTo(2.3);
    assertThat(dto.windSpeed().asWord()).isEqualTo(WindStrength.WEAK);
  }

  @Test
  @DisplayName("이미 이 발표시각의 예보가 저장되어 있으면 기상청을 다시 호출하지 않는다")
  void reusesStoredForecastsWhenAlreadyFetchedForThisBaseTime() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    WeatherAPILocation location = location(latitude, longitude);
    Grid existingGrid = Grid.builder().x(60).y(127).build();

    given(locationResolver.resolve(latitude, longitude)).willReturn(location);
    given(gridRepository.findByXAndY(60, 127)).willReturn(Optional.of(existingGrid));

    VilageFcstBaseTime baseTime = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(5, 0));
    given(baseTimeResolver.resolve(any())).willReturn(baseTime);

    Instant forecastedAt = LocalDateTime.of(2026, 7, 30, 5, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();
    Instant forecastAt = LocalDateTime.of(2026, 7, 30, 9, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();
    Weather existingWeather = Weather.builder()
        .grid(existingGrid)
        .forecastedAt(forecastedAt)
        .forecastAt(forecastAt)
        .skyStatus(SkyStatus.CLEAR)
        .precipitationType(PrecipitationType.NONE)
        .precipitationAmount(0.0)
        .precipitationProbability(20.0)
        .humidityCurrent(55.0)
        .temperatureCurrent(23.0)
        .windSpeed(2.3)
        .build();
    given(weatherRepository.findByGridAndForecastedAt(existingGrid, forecastedAt))
        .willReturn(List.of(existingWeather));

    // when
    List<WeatherDto> result = weatherService.getWeathers(latitude, longitude);

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).skyStatus()).isEqualTo(SkyStatus.CLEAR);
    verifyNoInteractions(kmaWeatherClient);
  }

  @Test
  @DisplayName("하루 전 같은 시각 기록이 있으면 전일 대비 값을 계산해서 저장한다")
  void computesComparedToDayBeforeWhenYesterdayRecordExists() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    WeatherAPILocation location = location(latitude, longitude);
    Grid existingGrid = Grid.builder().x(60).y(127).build();

    given(locationResolver.resolve(latitude, longitude)).willReturn(location);
    given(gridRepository.findByXAndY(60, 127)).willReturn(Optional.of(existingGrid));

    VilageFcstBaseTime baseTime = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(5, 0));
    given(baseTimeResolver.resolve(any())).willReturn(baseTime);

    VilageFcstItem item = new VilageFcstItem(
        LocalDateTime.of(2026, 7, 30, 5, 0),
        LocalDateTime.of(2026, 7, 30, 9, 0),
        SkyStatus.CLEAR,
        PrecipitationType.NONE,
        0.0,
        20.0,
        55.0,
        23.0,
        null,
        null,
        2.3,
        WindStrength.WEAK
    );
    given(kmaWeatherClient.getForecast(60, 127, baseTime)).willReturn(List.of(item));

    Instant forecastAt = LocalDateTime.of(2026, 7, 30, 9, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();
    Instant dayBeforeForecastAt = forecastAt.minus(1, ChronoUnit.DAYS);
    Weather yesterday = Weather.builder()
        .grid(existingGrid)
        .forecastedAt(dayBeforeForecastAt)
        .forecastAt(dayBeforeForecastAt)
        .skyStatus(SkyStatus.CLOUDY)
        .precipitationType(PrecipitationType.NONE)
        .humidityCurrent(50.0)
        .temperatureCurrent(20.0)
        .build();
    given(weatherRepository.findFirstByGridAndForecastAtOrderByForecastedAtDesc(existingGrid, dayBeforeForecastAt))
        .willReturn(Optional.of(yesterday));

    // when
    List<WeatherDto> result = weatherService.getWeathers(latitude, longitude);

    // then
    ArgumentCaptor<Weather> captor = ArgumentCaptor.forClass(Weather.class);
    verify(weatherSaver).saveInNewTransaction(captor.capture());
    Weather saved = captor.getValue();
    assertThat(saved.getHumidityComparedToDayBefore()).isEqualTo(5.0);
    assertThat(saved.getTemperatureComparedToDayBefore()).isEqualTo(3.0);

    assertThat(result.get(0).humidity().comparedToDayBefore()).isEqualTo(5.0);
    assertThat(result.get(0).temperature().comparedToDayBefore()).isEqualTo(3.0);
  }

  @Test
  @DisplayName("여러 날짜의 예보가 오면 날짜별로 대표 시간대 하나씩만 골라 반환한다")
  void returnsOnlyOneRepresentativeSlotPerDate() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    WeatherAPILocation location = location(latitude, longitude);
    Grid existingGrid = Grid.builder().x(60).y(127).build();

    given(locationResolver.resolve(latitude, longitude)).willReturn(location);
    given(gridRepository.findByXAndY(60, 127)).willReturn(Optional.of(existingGrid));

    VilageFcstBaseTime baseTime = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(5, 0));
    given(baseTimeResolver.resolve(any())).willReturn(baseTime);

    List<VilageFcstItem> items = List.of(
        vilageFcstItem(LocalDateTime.of(2026, 7, 30, 6, 0)),
        vilageFcstItem(LocalDateTime.of(2026, 7, 30, 9, 0)), // clock의 now와 정확히 일치 (대표 시각)
        vilageFcstItem(LocalDateTime.of(2026, 7, 30, 12, 0)),
        vilageFcstItem(LocalDateTime.of(2026, 7, 31, 8, 0)), // 대표시각(9시)과 1시간 차이 (최근접)
        vilageFcstItem(LocalDateTime.of(2026, 7, 31, 11, 0)),
        vilageFcstItem(LocalDateTime.of(2026, 8, 1, 9, 0)) // 대표시각과 정확히 일치
    );
    given(kmaWeatherClient.getForecast(60, 127, baseTime)).willReturn(items);

    // when
    List<WeatherDto> result = weatherService.getWeathers(latitude, longitude);

    // then
    ZoneId kst = ZoneId.of("Asia/Seoul");
    assertThat(result).extracting(WeatherDto::forecastAt).containsExactly(
        LocalDateTime.of(2026, 7, 30, 9, 0).atZone(kst).toInstant(),
        LocalDateTime.of(2026, 7, 31, 8, 0).atZone(kst).toInstant(),
        LocalDateTime.of(2026, 8, 1, 9, 0).atZone(kst).toInstant()
    );
  }

  private VilageFcstItem vilageFcstItem(LocalDateTime forecastAt) {
    return new VilageFcstItem(
        LocalDateTime.of(2026, 7, 30, 5, 0),
        forecastAt,
        SkyStatus.CLEAR,
        PrecipitationType.NONE,
        0.0,
        20.0,
        55.0,
        23.0,
        null,
        null,
        2.3,
        WindStrength.WEAK
    );
  }

  @Test
  @DisplayName("동시에 같은 예보가 먼저 저장돼 유니크 제약 위반이 나도, 이미 확보한 예보로 정상 반환한다")
  void returnsFreshForecastEvenWhenConcurrentSaveConflicts() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    WeatherAPILocation location = location(latitude, longitude);
    Grid existingGrid = Grid.builder().x(60).y(127).build();

    given(locationResolver.resolve(latitude, longitude)).willReturn(location);
    given(gridRepository.findByXAndY(60, 127)).willReturn(Optional.of(existingGrid));

    VilageFcstBaseTime baseTime = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(5, 0));
    given(baseTimeResolver.resolve(any())).willReturn(baseTime);

    VilageFcstItem item = new VilageFcstItem(
        LocalDateTime.of(2026, 7, 30, 5, 0),
        LocalDateTime.of(2026, 7, 30, 9, 0),
        SkyStatus.CLEAR,
        PrecipitationType.NONE,
        0.0,
        20.0,
        55.0,
        23.0,
        null,
        null,
        2.3,
        WindStrength.WEAK
    );
    given(kmaWeatherClient.getForecast(60, 127, baseTime)).willReturn(List.of(item));
    Mockito.doThrow(new DataIntegrityViolationException("duplicate key"))
        .when(weatherSaver).saveInNewTransaction(any(Weather.class));

    // when
    List<WeatherDto> result = weatherService.getWeathers(latitude, longitude);

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).skyStatus()).isEqualTo(SkyStatus.CLEAR);
  }

  @Test
  @DisplayName("캐시에 예보가 있으면 DB와 기상청 모두 건드리지 않고 캐시 값을 사용한다")
  void usesCachedForecastsWithoutTouchingDbOrKma() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    WeatherAPILocation location = location(latitude, longitude);

    given(locationResolver.resolve(latitude, longitude)).willReturn(location);

    VilageFcstBaseTime baseTime = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(5, 0));
    given(baseTimeResolver.resolve(any())).willReturn(baseTime);

    Instant forecastedAt = LocalDateTime.of(2026, 7, 30, 5, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();
    Instant forecastAt = LocalDateTime.of(2026, 7, 30, 9, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();
    WeatherDto cachedDto = new WeatherDto(
        null,
        forecastedAt,
        forecastAt,
        null, // 캐시에는 location이 없는 채로 저장되어 있음
        SkyStatus.CLEAR,
        new PrecipitationDto(PrecipitationType.NONE, 0.0, 20.0),
        new HumidityDto(55.0, 0.0),
        new TemperatureDto(23.0, 0.0, 20.0, 26.0),
        new WindSpeedDto(2.3, WindStrength.WEAK)
    );
    given(weatherForecastCache.find(new WeatherGrid(60, 127), forecastedAt))
        .willReturn(Optional.of(List.of(cachedDto)));

    // when
    List<WeatherDto> result = weatherService.getWeathers(latitude, longitude);

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).location().locationNames()).containsExactly("서울특별시", "강서구", "마곡동");
    assertThat(result.get(0).skyStatus()).isEqualTo(SkyStatus.CLEAR);
    verifyNoInteractions(gridRepository);
    verifyNoInteractions(weatherRepository);
    verifyNoInteractions(kmaWeatherClient);
    verifyNoInteractions(weatherSaver);
  }

  @Test
  @DisplayName("캐시가 비어 있어 기상청까지 호출하면, 날짜별로 고르기 전 전체 예보를 캐시에 저장한다")
  void savesFullForecastListToCacheAfterFetchingFromKma() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    WeatherAPILocation location = location(latitude, longitude);
    Grid existingGrid = Grid.builder().x(60).y(127).build();

    given(locationResolver.resolve(latitude, longitude)).willReturn(location);
    given(gridRepository.findByXAndY(60, 127)).willReturn(Optional.of(existingGrid));

    VilageFcstBaseTime baseTime = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(5, 0));
    given(baseTimeResolver.resolve(any())).willReturn(baseTime);

    List<VilageFcstItem> items = List.of(
        vilageFcstItem(LocalDateTime.of(2026, 7, 30, 9, 0)), // clock의 now와 정확히 일치
        vilageFcstItem(LocalDateTime.of(2026, 7, 30, 12, 0))
    );
    given(kmaWeatherClient.getForecast(60, 127, baseTime)).willReturn(items);

    Instant forecastedAt = LocalDateTime.of(2026, 7, 30, 5, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();

    // when
    List<WeatherDto> result = weatherService.getWeathers(latitude, longitude);

    // then
    assertThat(result).hasSize(1); // 응답은 대표 시각 하나로 좁혀짐

    ArgumentCaptor<List<WeatherDto>> captor = ArgumentCaptor.forClass(List.class);
    verify(weatherForecastCache).save(eq(new WeatherGrid(60, 127)), eq(forecastedAt), captor.capture());
    assertThat(captor.getValue()).hasSize(2); // 캐시에는 선택 전 전체 목록이 저장됨
  }

  @Test
  @DisplayName("캐시가 비어 있고 DB에 이미 저장된 예보를 찾으면, 그 값을 캐시에 저장한다")
  void savesDbSourcedForecastsToCacheOnCacheMiss() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    WeatherAPILocation location = location(latitude, longitude);
    Grid existingGrid = Grid.builder().x(60).y(127).build();

    given(locationResolver.resolve(latitude, longitude)).willReturn(location);
    given(gridRepository.findByXAndY(60, 127)).willReturn(Optional.of(existingGrid));

    VilageFcstBaseTime baseTime = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(5, 0));
    given(baseTimeResolver.resolve(any())).willReturn(baseTime);

    Instant forecastedAt = LocalDateTime.of(2026, 7, 30, 5, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();
    Instant forecastAt = LocalDateTime.of(2026, 7, 30, 9, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();
    Weather existingWeather = Weather.builder()
        .grid(existingGrid)
        .forecastedAt(forecastedAt)
        .forecastAt(forecastAt)
        .skyStatus(SkyStatus.CLEAR)
        .precipitationType(PrecipitationType.NONE)
        .precipitationAmount(0.0)
        .precipitationProbability(20.0)
        .humidityCurrent(55.0)
        .temperatureCurrent(23.0)
        .windSpeed(2.3)
        .build();
    given(weatherRepository.findByGridAndForecastedAt(existingGrid, forecastedAt))
        .willReturn(List.of(existingWeather));

    // when
    weatherService.getWeathers(latitude, longitude);

    // then
    verify(weatherForecastCache).save(eq(new WeatherGrid(60, 127)), eq(forecastedAt), any());
    verifyNoInteractions(kmaWeatherClient);
  }

  @Test
  @DisplayName("기상청 호출이 실패해도 캐시에 이전 판 데이터가 있으면 그걸로 폴백한다")
  void fallsBackToCachedPreviousForecastWhenKmaCallFails() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    WeatherAPILocation location = location(latitude, longitude);
    Grid existingGrid = Grid.builder().x(60).y(127).build();

    given(locationResolver.resolve(latitude, longitude)).willReturn(location);
    given(gridRepository.findByXAndY(60, 127)).willReturn(Optional.of(existingGrid));

    VilageFcstBaseTime baseTime = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(5, 0));
    given(baseTimeResolver.resolve(any())).willReturn(baseTime);
    VilageFcstBaseTime previousBaseTime = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(2, 0));
    given(baseTimeResolver.previous(baseTime)).willReturn(previousBaseTime);

    given(kmaWeatherClient.getForecast(60, 127, baseTime))
        .willThrow(new KmaApiException(60, 127, baseTime, new RuntimeException("기상청 장애")));

    Instant forecastedAt = LocalDateTime.of(2026, 7, 30, 5, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();
    given(weatherForecastCache.find(new WeatherGrid(60, 127), forecastedAt)).willReturn(Optional.empty());

    Instant previousForecastedAt = LocalDateTime.of(2026, 7, 30, 2, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();
    Instant previousForecastAt = LocalDateTime.of(2026, 7, 30, 6, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();
    WeatherDto cachedPrevious = new WeatherDto(
        null,
        previousForecastedAt,
        previousForecastAt,
        null,
        SkyStatus.CLEAR,
        new PrecipitationDto(PrecipitationType.NONE, 0.0, 20.0),
        new HumidityDto(55.0, 0.0),
        new TemperatureDto(23.0, 0.0, 20.0, 26.0),
        new WindSpeedDto(2.3, WindStrength.WEAK)
    );
    given(weatherForecastCache.find(new WeatherGrid(60, 127), previousForecastedAt))
        .willReturn(Optional.of(List.of(cachedPrevious)));

    // when
    List<WeatherDto> result = weatherService.getWeathers(latitude, longitude);

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).forecastedAt()).isEqualTo(previousForecastedAt);
    assertThat(result.get(0).location().locationNames()).containsExactly("서울특별시", "강서구", "마곡동");
  }

  @Test
  @DisplayName("기상청 호출이 실패하고 캐시에도 없지만 DB에 이전 판 데이터가 있으면 그걸로 폴백한다")
  void fallsBackToDbStoredPreviousForecastWhenKmaCallFailsAndCacheEmpty() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    WeatherAPILocation location = location(latitude, longitude);
    Grid existingGrid = Grid.builder().x(60).y(127).build();

    given(locationResolver.resolve(latitude, longitude)).willReturn(location);
    given(gridRepository.findByXAndY(60, 127)).willReturn(Optional.of(existingGrid));

    VilageFcstBaseTime baseTime = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(5, 0));
    given(baseTimeResolver.resolve(any())).willReturn(baseTime);
    VilageFcstBaseTime previousBaseTime = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(2, 0));
    given(baseTimeResolver.previous(baseTime)).willReturn(previousBaseTime);

    given(kmaWeatherClient.getForecast(60, 127, baseTime))
        .willThrow(new KmaApiException(60, 127, baseTime, new RuntimeException("기상청 장애")));

    Instant forecastedAt = LocalDateTime.of(2026, 7, 30, 5, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();
    given(weatherRepository.findByGridAndForecastedAt(existingGrid, forecastedAt)).willReturn(List.of());

    Instant previousForecastedAt = LocalDateTime.of(2026, 7, 30, 2, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();
    Instant previousForecastAt = LocalDateTime.of(2026, 7, 30, 6, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();
    Weather previousWeather = Weather.builder()
        .grid(existingGrid)
        .forecastedAt(previousForecastedAt)
        .forecastAt(previousForecastAt)
        .skyStatus(SkyStatus.CLOUDY)
        .precipitationType(PrecipitationType.NONE)
        .precipitationAmount(0.0)
        .precipitationProbability(10.0)
        .humidityCurrent(50.0)
        .temperatureCurrent(19.0)
        .windSpeed(1.5)
        .build();
    given(weatherRepository.findByGridAndForecastedAt(existingGrid, previousForecastedAt))
        .willReturn(List.of(previousWeather));

    // when
    List<WeatherDto> result = weatherService.getWeathers(latitude, longitude);

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).skyStatus()).isEqualTo(SkyStatus.CLOUDY);
    assertThat(result.get(0).forecastedAt()).isEqualTo(previousForecastedAt);
  }

  @Test
  @DisplayName("기상청 호출도 실패하고 이전 판 데이터도 전혀 없으면 예외가 그대로 전파된다")
  void propagatesExceptionWhenKmaCallFailsAndNoPreviousDataExistsAnywhere() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    WeatherAPILocation location = location(latitude, longitude);
    Grid existingGrid = Grid.builder().x(60).y(127).build();

    given(locationResolver.resolve(latitude, longitude)).willReturn(location);
    given(gridRepository.findByXAndY(60, 127)).willReturn(Optional.of(existingGrid));

    VilageFcstBaseTime baseTime = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(5, 0));
    given(baseTimeResolver.resolve(any())).willReturn(baseTime);
    VilageFcstBaseTime previousBaseTime = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(2, 0));
    given(baseTimeResolver.previous(baseTime)).willReturn(previousBaseTime);

    given(kmaWeatherClient.getForecast(60, 127, baseTime))
        .willThrow(new KmaApiException(60, 127, baseTime, new RuntimeException("기상청 장애")));

    // when & then
    assertThatThrownBy(() -> weatherService.getWeathers(latitude, longitude))
        .isInstanceOf(KmaApiException.class);
  }
}
