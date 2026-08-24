package com.otboo.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
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
import com.otboo.domain.weather.exception.GridRegistrationFailedException;
import com.otboo.domain.weather.exception.KmaApiException;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class WeatherForecastFinderTest {

  @Mock
  private GridResolver gridResolver;

  @Mock
  private VilageFcstBaseTimeResolver baseTimeResolver;

  @Mock
  private KmaWeatherClient kmaWeatherClient;

  @Mock
  private WeatherRepository weatherRepository;

  @Mock
  private WeatherPersister weatherPersister;

  @Mock
  private WeatherForecastCache weatherForecastCache;

  @Captor
  private ArgumentCaptor<List<WeatherDto>> savedForecastsCaptor;

  private final Clock clock = Clock.fixed(
      LocalDateTime.of(2026, 7, 30, 9, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant(),
      ZoneId.of("Asia/Seoul")
  );

  private WeatherForecastFinder weatherForecastFinder;

  @BeforeEach
  void setUp() {
    weatherForecastFinder = new WeatherForecastFinder(
        gridResolver,
        baseTimeResolver,
        kmaWeatherClient,
        weatherRepository,
        weatherPersister,
        new DailyForecastSelector(),
        clock,
        weatherForecastCache
    );

    // 캐시 조회를 별도로 stub하지 않는 테스트에서도 기본값(미스)을 받게 해준다.
    lenient().when(weatherForecastCache.find(any(), any())).thenReturn(Optional.empty());

    // weatherPersister는 이제 별도 유닛(WeatherPersisterTest)에서 저장/전일대비/동시성 로직을 검증하므로,
    // 여기서는 "item을 dto로 바꿔서 돌려준다" 정도의 단순 스텁으로 충분함 - 실제 변환 규칙은 VilageFcstItem.toDto와 동일.
    lenient().when(weatherPersister.persist(any(), any(), any())).thenAnswer(invocation -> {
      VilageFcstItem item = invocation.getArgument(0);
      WeatherAPILocation loc = invocation.getArgument(2);
      return Optional.of(item.toDto(loc));
    });
  }

  private WeatherAPILocation location(double latitude, double longitude) {
    return new WeatherAPILocation(latitude, longitude, 60, 127,
        List.of("서울특별시", "강서구", "마곡동"));
  }

  @Test
  @DisplayName("위치 정보와 기상청 예보를 조합해 목록을 반환한다")
  void returnsWeatherListCombiningLocationAndForecast() {
    // given
    WeatherAPILocation location = location(37.5665, 126.9780);
    Grid existingGrid = Grid.builder().x(60).y(127).build();

    given(gridResolver.findOrRegister(any())).willReturn(existingGrid);

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
        2.3
    );
    given(kmaWeatherClient.getForecast(60, 127, baseTime)).willReturn(Mono.just(List.of(item)));

    // when
    List<WeatherDto> result = weatherForecastFinder.find(location).block();

    // then
    assertThat(result).hasSize(1);
    WeatherDto dto = result.get(0);
    assertThat(dto.location().locationNames()).containsExactly("서울특별시", "강서구", "마곡동");
    assertThat(dto.skyStatus()).isEqualTo(SkyStatus.CLEAR);
    assertThat(dto.precipitation().type()).isEqualTo(PrecipitationType.NONE);
    assertThat(dto.precipitation().amount()).isEqualTo(0.0);
    assertThat(dto.precipitation().probability()).isEqualTo(20.0);
    verify(weatherPersister).persist(any(VilageFcstItem.class), eq(existingGrid), eq(location));
    assertThat(dto.humidity().current()).isEqualTo(55.0);
    assertThat(dto.temperature().current()).isEqualTo(23.0);
    assertThat(dto.windSpeed().speed()).isEqualTo(2.3);
    assertThat(dto.windSpeed().asWord()).isEqualTo(WindStrength.WEAK);
  }

  @Test
  @DisplayName("이미 이 발표시각의 예보가 저장되어 있으면 기상청을 다시 호출하지 않는다")
  void reusesStoredForecastsWhenAlreadyFetchedForThisBaseTime() {
    // given
    WeatherAPILocation location = location(37.5665, 126.9780);
    Grid existingGrid = Grid.builder().x(60).y(127).build();

    given(gridResolver.findOrRegister(any())).willReturn(existingGrid);

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
    List<WeatherDto> result = weatherForecastFinder.find(location).block();

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).skyStatus()).isEqualTo(SkyStatus.CLEAR);
    verifyNoInteractions(kmaWeatherClient);
  }

  @Test
  @DisplayName("여러 날짜의 예보가 오면 날짜별로 대표 시간대 하나씩만 골라 반환한다")
  void returnsOnlyOneRepresentativeSlotPerDate() {
    // given
    WeatherAPILocation location = location(37.5665, 126.9780);
    Grid existingGrid = Grid.builder().x(60).y(127).build();

    given(gridResolver.findOrRegister(any())).willReturn(existingGrid);

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
    given(kmaWeatherClient.getForecast(60, 127, baseTime)).willReturn(Mono.just(items));

    // when
    List<WeatherDto> result = weatherForecastFinder.find(location).block();

    // then
    ZoneId kst = ZoneId.of("Asia/Seoul");
    assertThat(result).extracting(WeatherDto::forecastAt).containsExactly(
        LocalDateTime.of(2026, 7, 30, 9, 0).atZone(kst).toInstant(),
        LocalDateTime.of(2026, 7, 31, 8, 0).atZone(kst).toInstant(),
        LocalDateTime.of(2026, 8, 1, 9, 0).atZone(kst).toInstant()
    );
    // 저장한 항목들이 걸치는 날짜(7/30, 7/31, 8/1) 각각에 대해 min/max 계산이 한 번씩만 이뤄져야 함
    verify(weatherPersister).resolveDailyMinMax(existingGrid, LocalDate.of(2026, 7, 30));
    verify(weatherPersister).resolveDailyMinMax(existingGrid, LocalDate.of(2026, 7, 31));
    verify(weatherPersister).resolveDailyMinMax(existingGrid, LocalDate.of(2026, 8, 1));
  }

  @Test
  @DisplayName("계산된 min/max를 응답 DTO에 반영하고, DB 반영은 별도로(백그라운드) 요청한다")
  void appliesResolvedRangeToResponseAndPersistsInBackground() {
    // given
    WeatherAPILocation location = location(37.5665, 126.9780);
    Grid existingGrid = Grid.builder().x(60).y(127).build();

    given(gridResolver.findOrRegister(any())).willReturn(existingGrid);

    VilageFcstBaseTime baseTime = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(5, 0));
    given(baseTimeResolver.resolve(any())).willReturn(baseTime);

    VilageFcstItem item = vilageFcstItem(LocalDateTime.of(2026, 7, 30, 9, 0));
    given(kmaWeatherClient.getForecast(60, 127, baseTime)).willReturn(Mono.just(List.of(item)));

    WeatherPersister.DailyTemperatureRange resolvedRange = new WeatherPersister.DailyTemperatureRange(15.0, 26.0);
    given(weatherPersister.resolveDailyMinMax(existingGrid, LocalDate.of(2026, 7, 30)))
        .willReturn(Optional.of(resolvedRange));

    // when
    List<WeatherDto> result = weatherForecastFinder.find(location).block();

    // then: 응답 DTO의 min/max가 resolveDailyMinMax가 계산해준 값으로 덮어써짐
    assertThat(result).hasSize(1);
    assertThat(result.get(0).temperature().min()).isEqualTo(15.0);
    assertThat(result.get(0).temperature().max()).isEqualTo(26.0);

    // then: DB 반영(persistDailyMinMax)은 응답과 별개로 백그라운드에서 실행되므로(구독만 하고 안 기다림),
    // block() 리턴 시점엔 아직 안 끝났을 수 있어 timeout으로 폴링해서 확인한다.
    verify(weatherPersister, Mockito.timeout(1000))
        .persistDailyMinMax(existingGrid, LocalDate.of(2026, 7, 30), 15.0, 26.0);
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
        2.3
    );
  }

  @Test
  @DisplayName("캐시에 있는 오늘 대표가 지금 시각 기준으로도 여전히 최근접이면, 기상청은 안 부르고 캐시 값을 그대로 쓴다")
  void usesCachedForecastsWithoutTouchingKmaWhenStillFresh() {
    // given
    WeatherAPILocation location = location(37.5665, 126.9780);
    Grid existingGrid = Grid.builder().x(60).y(127).build();

    VilageFcstBaseTime baseTime = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(5, 0));
    given(baseTimeResolver.resolve(any())).willReturn(baseTime);

    Instant forecastedAt = LocalDateTime.of(2026, 7, 30, 5, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();
    Instant forecastAt = LocalDateTime.of(2026, 7, 30, 9, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant(); // clock과 정확히 일치 = 지금도 최근접
    WeatherDto cachedDto = new WeatherDto(
        null,
        forecastedAt,
        forecastAt,
        null, // 캐시에는 location이 없는 채로 저장되어 있음
        SkyStatus.CLEAR,
        new PrecipitationDto(PrecipitationType.NONE, 0.0, 20.0),
        new HumidityDto(55.0, 0.0),
        new TemperatureDto(23.0, 0.0, 20.0, 26.0, 23.0),
        new WindSpeedDto(2.3, WindStrength.WEAK)
    );
    given(weatherForecastCache.find(new WeatherGrid(60, 127), forecastedAt))
        .willReturn(Optional.of(List.of(cachedDto)));
    // 캐시 히트여도 "오늘 대표가 지금도 최근접인지" DB로 검증하므로, 검증용 조회 스텁이 필요함 -
    // 캐시와 같은 forecastAt짜리 row 하나만 있으면 재계산 없이 캐시가 그대로 살아남는다.
    given(gridResolver.findOrRegister(any())).willReturn(existingGrid);
    given(weatherRepository.findByGridAndForecastedAt(existingGrid, forecastedAt))
        .willReturn(List.of(Weather.builder()
            .grid(existingGrid)
            .forecastedAt(forecastedAt)
            .forecastAt(forecastAt)
            .skyStatus(SkyStatus.CLEAR)
            .precipitationType(PrecipitationType.NONE)
            .build()));

    // when
    List<WeatherDto> result = weatherForecastFinder.find(location).block();

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).location().locationNames()).containsExactly("서울특별시", "강서구", "마곡동");
    assertThat(result.get(0).skyStatus()).isEqualTo(SkyStatus.CLEAR);
    verifyNoInteractions(kmaWeatherClient);
    verifyNoInteractions(weatherPersister);
    // 이미 최신이니 캐시를 다시 쓰지 않는다(불필요한 Redis 쓰기 없음).
    Mockito.verify(weatherForecastCache, Mockito.never()).save(any(), any(), any());
  }

  @Test
  @DisplayName("캐시에 있는 오늘 대표가 지금 시각 기준으로 더는 최근접이 아니면, DB 기준으로 다시 뽑아 캐시까지 갱신한다")
  void refreshesCacheWhenTodayRepresentativeIsStale() {
    // given: 캐시엔 05시 슬롯이 대표로 박혀있지만, clock(09:00)엔 09시 슬롯이 진짜 최근접임
    WeatherAPILocation location = location(37.5665, 126.9780);
    Grid existingGrid = Grid.builder().x(60).y(127).build();

    VilageFcstBaseTime baseTime = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(5, 0));
    given(baseTimeResolver.resolve(any())).willReturn(baseTime);

    Instant forecastedAt = LocalDateTime.of(2026, 7, 30, 5, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();
    Instant staleForecastAt = LocalDateTime.of(2026, 7, 30, 5, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();
    Instant freshForecastAt = LocalDateTime.of(2026, 7, 30, 9, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();
    WeatherDto staleCachedDto = new WeatherDto(
        null, forecastedAt, staleForecastAt, null,
        SkyStatus.CLEAR,
        new PrecipitationDto(PrecipitationType.NONE, 0.0, 20.0),
        new HumidityDto(55.0, 0.0),
        new TemperatureDto(20.0, 0.0, 18.0, 26.0, 20.0),
        new WindSpeedDto(2.3, WindStrength.WEAK)
    );
    given(weatherForecastCache.find(new WeatherGrid(60, 127), forecastedAt))
        .willReturn(Optional.of(List.of(staleCachedDto)));
    given(gridResolver.findOrRegister(any())).willReturn(existingGrid);
    given(weatherRepository.findByGridAndForecastedAt(existingGrid, forecastedAt))
        .willReturn(List.of(
            Weather.builder().grid(existingGrid).forecastedAt(forecastedAt).forecastAt(staleForecastAt)
                .skyStatus(SkyStatus.CLEAR).precipitationType(PrecipitationType.NONE)
                .temperatureCurrent(20.0).temperatureMin(18.0).temperatureMax(26.0).build(),
            Weather.builder().grid(existingGrid).forecastedAt(forecastedAt).forecastAt(freshForecastAt)
                .skyStatus(SkyStatus.MOSTLY_CLOUDY).precipitationType(PrecipitationType.NONE)
                .temperatureCurrent(26.0).temperatureMin(18.0).temperatureMax(26.0).build()
        ));

    // when
    List<WeatherDto> result = weatherForecastFinder.find(location).block();

    // then: 05시가 아니라 09시(지금과 정확히 일치) 슬롯이 대표로 나와야 함
    assertThat(result).hasSize(1);
    assertThat(result.get(0).forecastAt()).isEqualTo(freshForecastAt);
    assertThat(result.get(0).skyStatus()).isEqualTo(SkyStatus.MOSTLY_CLOUDY);
    verifyNoInteractions(kmaWeatherClient);
    verify(weatherForecastCache).save(eq(new WeatherGrid(60, 127)), eq(forecastedAt), savedForecastsCaptor.capture());
    assertThat(savedForecastsCaptor.getValue()).extracting(WeatherDto::forecastAt).containsExactly(freshForecastAt);
  }

  @Test
  @DisplayName("캐시는 있는데 검증용 DB 조회가 비어있으면(비정상), 캐시를 못 믿고 기상청부터 다시 받아온다")
  void fetchesFromKmaWhenCacheExistsButDbValidationIsEmpty() {
    // given
    WeatherAPILocation location = location(37.5665, 126.9780);
    Grid existingGrid = Grid.builder().x(60).y(127).build();

    VilageFcstBaseTime baseTime = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(5, 0));
    given(baseTimeResolver.resolve(any())).willReturn(baseTime);

    Instant forecastedAt = LocalDateTime.of(2026, 7, 30, 5, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();
    Instant cachedForecastAt = LocalDateTime.of(2026, 7, 30, 9, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();
    WeatherDto cachedDto = new WeatherDto(
        null, forecastedAt, cachedForecastAt, null,
        SkyStatus.CLEAR,
        new PrecipitationDto(PrecipitationType.NONE, 0.0, 20.0),
        new HumidityDto(55.0, 0.0),
        new TemperatureDto(23.0, 0.0, 20.0, 26.0, 23.0),
        new WindSpeedDto(2.3, WindStrength.WEAK)
    );
    given(weatherForecastCache.find(new WeatherGrid(60, 127), forecastedAt))
        .willReturn(Optional.of(List.of(cachedDto)));
    given(gridResolver.findOrRegister(any())).willReturn(existingGrid);
    // 검증용 조회가 비어있음 - 캐시가 가리키는 DB 데이터가 사라진 비정상 상황을 흉내냄.
    given(weatherRepository.findByGridAndForecastedAt(existingGrid, forecastedAt))
        .willReturn(List.of());

    List<VilageFcstItem> items = List.of(vilageFcstItem(LocalDateTime.of(2026, 7, 30, 9, 0)));
    given(kmaWeatherClient.getForecast(60, 127, baseTime)).willReturn(Mono.just(items));

    // when
    List<WeatherDto> result = weatherForecastFinder.find(location).block();

    // then: 캐시 값이 아니라 기상청에서 새로 받은 값으로 응답/캐시 저장이 이뤄진다
    assertThat(result).hasSize(1);
    Mockito.verify(kmaWeatherClient).getForecast(60, 127, baseTime);
    verify(weatherForecastCache).save(eq(new WeatherGrid(60, 127)), eq(forecastedAt), any());
  }

  @Test
  @DisplayName("캐시가 비어 있어 기상청까지 호출하면, 날짜별로 고른 대표 예보 목록 전체를 이 발표의 캐시 키 하나에 저장한다")
  void savesDailyRepresentativeToCachePerDateAfterFetchingFromKma() {
    // given
    WeatherAPILocation location = location(37.5665, 126.9780);
    Grid existingGrid = Grid.builder().x(60).y(127).build();

    given(gridResolver.findOrRegister(any())).willReturn(existingGrid);

    VilageFcstBaseTime baseTime = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(5, 0));
    given(baseTimeResolver.resolve(any())).willReturn(baseTime);

    List<VilageFcstItem> items = List.of(
        vilageFcstItem(LocalDateTime.of(2026, 7, 30, 9, 0)), // clock의 now와 정확히 일치
        vilageFcstItem(LocalDateTime.of(2026, 7, 30, 12, 0))
    );
    given(kmaWeatherClient.getForecast(60, 127, baseTime)).willReturn(Mono.just(items));

    Instant forecastedAt = LocalDateTime.of(2026, 7, 30, 5, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();
    ZoneId kst = ZoneId.of("Asia/Seoul");

    // when
    List<WeatherDto> result = weatherForecastFinder.find(location).block();

    // then
    assertThat(result).hasSize(1); // 같은 날짜(7/30) 슬롯 2개가 대표 하나로 좁혀짐

    verify(weatherForecastCache).save(eq(new WeatherGrid(60, 127)), eq(forecastedAt), savedForecastsCaptor.capture());
    assertThat(savedForecastsCaptor.getValue()).hasSize(1);
    assertThat(savedForecastsCaptor.getValue().get(0).forecastAt())
        .isEqualTo(LocalDateTime.of(2026, 7, 30, 9, 0).atZone(kst).toInstant());
  }

  @Test
  @DisplayName("캐시가 비어 있고 DB에 이미 저장된 예보를 찾으면, 그 값을 캐시에 저장한다")
  void savesDbSourcedForecastsToCacheOnCacheMiss() {
    // given
    WeatherAPILocation location = location(37.5665, 126.9780);
    Grid existingGrid = Grid.builder().x(60).y(127).build();

    given(gridResolver.findOrRegister(any())).willReturn(existingGrid);

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
    weatherForecastFinder.find(location).block();

    // then
    verify(weatherForecastCache).save(eq(new WeatherGrid(60, 127)), eq(forecastedAt), any());
    verifyNoInteractions(kmaWeatherClient);
  }

  @Test
  @DisplayName("기상청 호출이 실패해도 캐시에 이전 판 데이터가 있으면 그걸로 폴백한다")
  void fallsBackToCachedPreviousForecastWhenKmaCallFails() {
    // given
    WeatherAPILocation location = location(37.5665, 126.9780);
    Grid existingGrid = Grid.builder().x(60).y(127).build();

    given(gridResolver.findOrRegister(any())).willReturn(existingGrid);

    VilageFcstBaseTime baseTime = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(5, 0));
    given(baseTimeResolver.resolve(any())).willReturn(baseTime);
    VilageFcstBaseTime previousBaseTime = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(2, 0));
    given(baseTimeResolver.previous(baseTime)).willReturn(previousBaseTime);

    given(kmaWeatherClient.getForecast(60, 127, baseTime))
        .willThrow(new KmaApiException(60, 127, baseTime, new RuntimeException("기상청 장애")));

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
        new TemperatureDto(23.0, 0.0, 20.0, 26.0, 23.0),
        new WindSpeedDto(2.3, WindStrength.WEAK)
    );
    given(weatherForecastCache.find(new WeatherGrid(60, 127), previousForecastedAt))
        .willReturn(Optional.of(List.of(cachedPrevious)));

    // when
    List<WeatherDto> result = weatherForecastFinder.find(location).block();

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).forecastedAt()).isEqualTo(previousForecastedAt);
    assertThat(result.get(0).location().locationNames()).containsExactly("서울특별시", "강서구", "마곡동");
  }

  @Test
  @DisplayName("기상청 호출이 실패하고 캐시에도 없지만 DB에 이전 판 데이터가 있으면 그걸로 폴백한다")
  void fallsBackToDbStoredPreviousForecastWhenKmaCallFailsAndCacheEmpty() {
    // given
    WeatherAPILocation location = location(37.5665, 126.9780);
    Grid existingGrid = Grid.builder().x(60).y(127).build();

    given(gridResolver.findOrRegister(any())).willReturn(existingGrid);

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
    List<WeatherDto> result = weatherForecastFinder.find(location).block();

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).skyStatus()).isEqualTo(SkyStatus.CLOUDY);
    assertThat(result.get(0).forecastedAt()).isEqualTo(previousForecastedAt);
  }

  @Test
  @DisplayName("기상청 호출도 실패하고 이전 판 데이터도 전혀 없으면 예외가 그대로 전파된다")
  void propagatesExceptionWhenKmaCallFailsAndNoPreviousDataExistsAnywhere() {
    // given
    WeatherAPILocation location = location(37.5665, 126.9780);
    Grid existingGrid = Grid.builder().x(60).y(127).build();

    given(gridResolver.findOrRegister(any())).willReturn(existingGrid);

    VilageFcstBaseTime baseTime = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(5, 0));
    given(baseTimeResolver.resolve(any())).willReturn(baseTime);
    VilageFcstBaseTime previousBaseTime = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(2, 0));
    given(baseTimeResolver.previous(baseTime)).willReturn(previousBaseTime);

    given(kmaWeatherClient.getForecast(60, 127, baseTime))
        .willThrow(new KmaApiException(60, 127, baseTime, new RuntimeException("기상청 장애")));

    // when & then
    assertThatThrownBy(() -> weatherForecastFinder.find(location).block())
        .isInstanceOf(KmaApiException.class);
  }

  @Test
  @DisplayName("격자를 재등록했는데도 여전히 못 찾으면 GridRegistrationFailedException을 던진다")
  void throwsGridRegistrationFailedExceptionWhenGridStillMissingAfterReregistration() {
    // given
    WeatherAPILocation location = location(37.5665, 126.9780);

    // GridResolver가 등록까지 시도했는데도 끝내 못 찾은 상황을 흉내낸다(실제 재시도 메커니즘 자체는 GridResolverTest에서 검증).
    given(gridResolver.findOrRegister(any()))
        .willThrow(new GridRegistrationFailedException(60, 127));

    VilageFcstBaseTime baseTime = new VilageFcstBaseTime(LocalDate.of(2026, 7, 30), LocalTime.of(5, 0));
    given(baseTimeResolver.resolve(any())).willReturn(baseTime);

    // when & then
    assertThatThrownBy(() -> weatherForecastFinder.find(location).block())
        .isInstanceOf(GridRegistrationFailedException.class);
  }
}
