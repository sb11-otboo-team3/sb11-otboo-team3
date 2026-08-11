package com.otboo.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.otboo.domain.weather.dto.HumidityDto;
import com.otboo.domain.weather.dto.PrecipitationDto;
import com.otboo.domain.weather.dto.TemperatureDto;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.dto.WeatherDto;
import com.otboo.domain.weather.dto.WindSpeedDto;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.entity.WindStrength;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

// WeatherServiceImpl은 이제 getLocation/getWeathers를 각각 LocationResolver/WeatherForecastFinder에
// 위임만 하는 얇은 파사드라, 여기서는 위임이 제대로 되는지만 검증한다. 실제 조회 로직은
// WeatherForecastFinderTest, 요약 조회는 WeatherSummaryFinderTest 참고.
@ExtendWith(MockitoExtension.class)
class WeatherServiceImplTest {

  @Mock
  private LocationResolver locationResolver;

  @Mock
  private WeatherForecastFinder weatherForecastFinder;

  private WeatherServiceImpl weatherService;

  @BeforeEach
  void setUp() {
    weatherService = new WeatherServiceImpl(locationResolver, weatherForecastFinder);
  }

  private WeatherAPILocation location(double latitude, double longitude) {
    return new WeatherAPILocation(latitude, longitude, 60, 127,
        List.of("서울특별시", "강서구", "마곡동"));
  }

  @Test
  @DisplayName("getLocation은 LocationResolver에게 위임한다")
  void getLocationDelegatesToLocationResolver() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    WeatherAPILocation location = location(latitude, longitude);
    given(locationResolver.resolve(latitude, longitude)).willReturn(Mono.just(location));

    // when
    WeatherAPILocation result = weatherService.getLocation(latitude, longitude).block();

    // then
    assertThat(result).isEqualTo(location);
    verify(locationResolver).resolve(latitude, longitude);
  }

  @Test
  @DisplayName("getWeathers는 위치를 해석한 뒤 WeatherForecastFinder에게 위임한다")
  void getWeathersDelegatesToWeatherForecastFinderAfterResolvingLocation() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    WeatherAPILocation location = location(latitude, longitude);
    WeatherDto dto = new WeatherDto(
        UUID.randomUUID(),
        Instant.parse("2026-07-30T00:00:00Z"),
        Instant.parse("2026-07-30T09:00:00Z"),
        location,
        SkyStatus.CLEAR,
        new PrecipitationDto(PrecipitationType.NONE, 0.0, 0.0),
        new HumidityDto(55.0, 0.0),
        new TemperatureDto(23.0, 0.0, 20.0, 26.0),
        new WindSpeedDto(2.3, WindStrength.WEAK)
    );

    given(locationResolver.resolve(latitude, longitude)).willReturn(Mono.just(location));
    given(weatherForecastFinder.find(location)).willReturn(Mono.just(List.of(dto)));

    // when
    List<WeatherDto> result = weatherService.getWeathers(latitude, longitude).block();

    // then
    assertThat(result).containsExactly(dto);
    verify(weatherForecastFinder).find(location);
  }
}
