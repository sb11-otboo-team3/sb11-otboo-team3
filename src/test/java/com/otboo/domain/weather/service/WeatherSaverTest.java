package com.otboo.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.entity.Weather;
import com.otboo.domain.weather.repository.WeatherRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeatherSaverTest {

  @Mock
  private WeatherRepository weatherRepository;

  private WeatherSaver weatherSaver;

  private final Grid grid = Grid.builder().x(60).y(127).build();

  @BeforeEach
  void setUp() {
    weatherSaver = new WeatherSaver(weatherRepository);
  }

  private Weather newWeather() {
    return Weather.builder()
        .grid(grid)
        .forecastedAt(Instant.parse("2026-07-30T00:00:00Z"))
        .forecastAt(Instant.parse("2026-07-30T09:00:00Z"))
        .skyStatus(SkyStatus.CLEAR)
        .precipitationType(PrecipitationType.NONE)
        .precipitationAmount(0.0)
        .precipitationProbability(20.0)
        .humidityCurrent(55.0)
        .humidityComparedToDayBefore(5.0)
        .temperatureCurrent(23.0)
        .temperatureComparedToDayBefore(3.0)
        .temperatureMin(18.0)
        .temperatureMax(27.0)
        .windSpeed(2.3)
        .build();
  }

  @Test
  @DisplayName("weather의 필드값들을 그대로 넘겨 upsert를 호출하고, 그 결과를 반환한다")
  void upsertsWithWeatherFieldsAndReturnsResult() {
    // given: weather.getId()는 저장 전(transient)이라 항상 null이므로, upsert에 실제로 넘어가는 id는
    // 이 메서드가 새로 생성한 값이다 - any(UUID.class)로만 "UUID 하나가 넘어갔다"는 것만 확인한다.
    Weather weather = newWeather();
    Weather upserted = newWeather();
    given(weatherRepository.upsert(
        any(UUID.class), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any(), any(), any(), any()
    )).willReturn(Optional.of(upserted));

    // when
    Weather result = weatherSaver.upsertInNewTransaction(weather);

    // then
    assertThat(result).isSameAs(upserted);
    verify(weatherRepository).upsert(
        any(UUID.class),
        eq(grid.getId()),
        eq(weather.getForecastedAt()),
        eq(weather.getForecastAt()),
        eq("CLEAR"),
        eq("NONE"),
        eq(0.0),
        eq(20.0),
        eq(55.0),
        eq(5.0),
        eq(23.0),
        eq(3.0),
        eq(18.0),
        eq(27.0),
        eq(2.3)
    );
  }

  @Test
  @DisplayName("upsert가 비어있으면(더 최신 forecastedAt이 이미 있어 스킵됨) 현재 row를 재조회해서 반환한다")
  void fallsBackToExistingRowWhenUpsertIsSkipped() {
    // given
    Weather weather = newWeather();
    given(weatherRepository.upsert(
        any(UUID.class), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any(), any(), any(), any()
    )).willReturn(Optional.empty());

    Weather existing = newWeather();
    given(weatherRepository.findByGridAndForecastAt(weather.getGrid(), weather.getForecastAt()))
        .willReturn(Optional.of(existing));

    // when
    Weather result = weatherSaver.upsertInNewTransaction(weather);

    // then
    assertThat(result).isSameAs(existing);
  }

  @Test
  @DisplayName("upsert가 스킵됐는데 재조회로도 못 찾으면 예외를 던진다(있어선 안 되는 상태)")
  void throwsWhenUpsertSkippedAndRefetchAlsoMisses() {
    // given
    Weather weather = newWeather();
    given(weatherRepository.upsert(
        any(UUID.class), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any(), any(), any(), any()
    )).willReturn(Optional.empty());
    given(weatherRepository.findByGridAndForecastAt(weather.getGrid(), weather.getForecastAt()))
        .willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> weatherSaver.upsertInNewTransaction(weather))
        .isInstanceOf(IllegalStateException.class);
  }
}
