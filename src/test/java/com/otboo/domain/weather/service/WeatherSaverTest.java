package com.otboo.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.entity.Weather;
import com.otboo.domain.weather.repository.WeatherRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class WeatherSaverTest {

  @Mock
  private WeatherRepository weatherRepository;

  private WeatherSaver weatherSaver;

  @BeforeEach
  void setUp() {
    weatherSaver = new WeatherSaver(weatherRepository);
  }

  private Weather newWeather() {
    Grid grid = Grid.builder().x(60).y(127).build();
    return Weather.builder()
        .grid(grid)
        .forecastedAt(Instant.parse("2026-07-30T00:00:00Z"))
        .forecastAt(Instant.parse("2026-07-30T09:00:00Z"))
        .skyStatus(SkyStatus.CLEAR)
        .precipitationType(PrecipitationType.NONE)
        .build();
  }

  @Test
  @DisplayName("정상 저장이면 saveAndFlush로 즉시 반영한다")
  void savesAndFlushesWeather() {
    // given
    Weather weather = newWeather();

    // when
    weatherSaver.saveInNewTransaction(weather);

    // then
    verify(weatherRepository).saveAndFlush(weather);
  }

  @Test
  @DisplayName("동시 저장으로 유니크 제약 위반이 나면 예외를 그대로 던진다 (호출부에서 잡아야 하므로 여기선 삼키지 않는다)")
  void propagatesDataIntegrityViolationOnConcurrentSave() {
    // given
    Weather weather = newWeather();
    given(weatherRepository.saveAndFlush(any(Weather.class)))
        .willThrow(new DataIntegrityViolationException("duplicate key"));

    // when & then
    assertThatThrownBy(() -> weatherSaver.saveInNewTransaction(weather))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}