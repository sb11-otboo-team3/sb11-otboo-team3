package com.otboo.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.otboo.domain.weather.cache.GridRecencyCache;
import com.otboo.domain.weather.client.KakaoLocationClient;
import com.otboo.domain.weather.client.KmaWeatherClient;
import com.otboo.domain.weather.dto.KakaoRegion;
import com.otboo.domain.weather.dto.VilageFcstItem;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.dto.WeatherDto;
import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.entity.Weather;
import com.otboo.domain.weather.entity.WindStrength;
import com.otboo.domain.weather.repository.GridRepository;
import com.otboo.domain.weather.repository.WeatherRepository;
import com.otboo.domain.weather.util.GridConverter;
import com.otboo.domain.weather.util.VilageFcstBaseTime;
import com.otboo.domain.weather.util.VilageFcstBaseTimeResolver;
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
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class WeatherServiceImplTest {

  @Mock
  private GridRepository gridRepository;

  @Mock
  private KakaoLocationClient kakaoLocationClient;

  @Mock
  private GridRecencyCache gridRecencyCache;

  @Mock
  private GridSaver gridSaver;

  @Mock
  private VilageFcstBaseTimeResolver baseTimeResolver;

  @Mock
  private KmaWeatherClient kmaWeatherClient;

  @Mock
  private WeatherRepository weatherRepository;

  private WeatherServiceImpl weatherService;

  @BeforeEach
  void setUp() {
    weatherService = new WeatherServiceImpl(
        new GridConverter(),
        gridRepository,
        kakaoLocationClient,
        gridRecencyCache,
        gridSaver,
        baseTimeResolver,
        kmaWeatherClient,
        weatherRepository
    );
  }

  @Test
  @DisplayName("항상 카카오 API를 호출해 정확한 행정구역으로 응답한다")
  void alwaysCallsKakaoAndRespondsWithFreshRegion() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    KakaoRegion region = new KakaoRegion("서울특별시", "강서구", "마곡동");

    given(kakaoLocationClient.getRegion(latitude, longitude)).willReturn(region);
    given(gridRepository.findByXAndY(60, 127)).willReturn(Optional.empty());

    // when
    WeatherAPILocation result = weatherService.getLocation(latitude, longitude);

    // then
    assertThat(result.latitude()).isEqualTo(latitude);
    assertThat(result.longitude()).isEqualTo(longitude);
    assertThat(result.x()).isEqualTo(60);
    assertThat(result.y()).isEqualTo(127);
    assertThat(result.locationNames()).containsExactly("서울특별시", "강서구", "마곡동");
    verify(kakaoLocationClient).getRegion(latitude, longitude);
  }

  @Test
  @DisplayName("최근에 확인된 격자면 DB를 건드리지 않고 바로 응답한다")
  void skipsDbWhenRecentlyConfirmed() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    KakaoRegion region = new KakaoRegion("서울특별시", "강서구", "마곡동");

    given(kakaoLocationClient.getRegion(latitude, longitude)).willReturn(region);
    given(gridRecencyCache.isRecentlyConfirmed(any())).willReturn(true);

    // when
    WeatherAPILocation result = weatherService.getLocation(latitude, longitude);

    // then
    assertThat(result.locationNames()).containsExactly("서울특별시", "강서구", "마곡동");
    verifyNoInteractions(gridRepository);
    verifyNoInteractions(gridSaver);
  }

  @Test
  @DisplayName("이미 등록된 격자면 새로 저장하지 않고 최근 요청 시각만 갱신한다")
  void refreshesRecencyWhenGridAlreadyRegistered() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    KakaoRegion region = new KakaoRegion("서울특별시", "강서구", "마곡동");
    Grid existing = Mockito.spy(Grid.builder().x(60).y(127).build());

    given(kakaoLocationClient.getRegion(latitude, longitude)).willReturn(region);
    given(gridRepository.findByXAndY(60, 127)).willReturn(Optional.of(existing));

    // when
    WeatherAPILocation result = weatherService.getLocation(latitude, longitude);

    // then
    assertThat(result.locationNames()).containsExactly("서울특별시", "강서구", "마곡동");
    verify(existing).refreshRequestedAt();
    verify(gridRepository).save(existing);
    verify(gridSaver, never()).saveInNewTransaction(any(Grid.class));
    verify(gridRecencyCache).markConfirmed(any());
  }

  @Test
  @DisplayName("처음 보는 격자면 레지스트리에 등록한다")
  void registersNewGridWhenNotYetRegistered() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    KakaoRegion region = new KakaoRegion("서울특별시", "강서구", "마곡동");

    given(kakaoLocationClient.getRegion(latitude, longitude)).willReturn(region);
    given(gridRepository.findByXAndY(60, 127)).willReturn(Optional.empty());

    // when
    weatherService.getLocation(latitude, longitude);

    // then
    ArgumentCaptor<Grid> captor = ArgumentCaptor.forClass(Grid.class);
    verify(gridSaver).saveInNewTransaction(captor.capture());
    Grid saved = captor.getValue();
    assertThat(saved.getX()).isEqualTo(60);
    assertThat(saved.getY()).isEqualTo(127);
    verify(gridRecencyCache).markConfirmed(any());
  }

  @Test
  @DisplayName("동시에 같은 격자가 먼저 등록돼 유니크 제약 위반이 나도, 이미 확보한 카카오 응답으로 정상 반환한다")
  void returnsFreshRegionEvenWhenConcurrentSaveConflicts() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    KakaoRegion region = new KakaoRegion("서울특별시", "강서구", "마곡동");

    given(kakaoLocationClient.getRegion(latitude, longitude)).willReturn(region);
    given(gridRepository.findByXAndY(60, 127)).willReturn(Optional.empty());
    Mockito.doThrow(new DataIntegrityViolationException("duplicate key"))
        .when(gridSaver).saveInNewTransaction(any(Grid.class));

    // when
    WeatherAPILocation result = weatherService.getLocation(latitude, longitude);

    // then
    assertThat(result.locationNames()).containsExactly("서울특별시", "강서구", "마곡동");
    verify(gridRecencyCache).markConfirmed(any());
  }

  @Test
  @DisplayName("위경도로 조회하면 위치 정보와 기상청 예보를 조합해 목록을 반환한다")
  void returnsWeatherListCombiningLocationAndForecast() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    KakaoRegion region = new KakaoRegion("서울특별시", "강서구", "마곡동");
    Grid existingGrid = Grid.builder().x(60).y(127).build();

    given(kakaoLocationClient.getRegion(latitude, longitude)).willReturn(region);
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
    KakaoRegion region = new KakaoRegion("서울특별시", "강서구", "마곡동");
    Grid existingGrid = Grid.builder().x(60).y(127).build();

    given(kakaoLocationClient.getRegion(latitude, longitude)).willReturn(region);
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
}