package com.otboo.domain.weather.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class WeatherEntityTest {

  @Autowired
  private EntityManager entityManager;

  private Grid persistGrid(int x, int y) {
    Grid grid = Grid.builder().x(x).y(y).build();
    entityManager.persist(grid);
    entityManager.flush();
    return grid;
  }

  @Test
  @DisplayName("같은 격자, 같은 예보대상시각이면 예보발표시각이 달라도 중복 저장할 수 없다")
  void throwsExceptionWhenSameGridAndForecastAtEvenWithDifferentForecastedAt() {
    // given: 유니크 키가 (grid_id, forecast_at)뿐이라 forecasted_at은 달라도 막혀야 한다 -
    // upsert가 이 상황을 처리하는 전제(WeatherSaver 참고) 그대로.
    Grid grid = persistGrid(60, 127);
    Instant forecastAt = Instant.parse("2026-07-30T09:00:00Z");

    Weather first = Weather.builder()
        .grid(grid)
        .forecastedAt(Instant.parse("2026-07-30T00:00:00Z"))
        .forecastAt(forecastAt)
        .skyStatus(SkyStatus.CLEAR)
        .precipitationType(PrecipitationType.NONE)
        .build();
    entityManager.persist(first);
    entityManager.flush();

    Weather duplicate = Weather.builder()
        .grid(grid)
        .forecastedAt(Instant.parse("2026-07-30T03:00:00Z")) // 발표시각은 다름
        .forecastAt(forecastAt) // 대상시각은 같음
        .skyStatus(SkyStatus.CLOUDY)
        .precipitationType(PrecipitationType.RAIN)
        .build();

    // when & then
    assertThatThrownBy(() -> {
      entityManager.persist(duplicate);
      entityManager.flush();
    }).isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  @DisplayName("같은 격자라도 예보대상시각이 다르면 함께 저장할 수 있다")
  void allowsSameGridWithDifferentForecastAt() {
    // given
    Grid grid = persistGrid(60, 127);
    Weather first = Weather.builder()
        .grid(grid)
        .forecastedAt(Instant.parse("2026-07-30T00:00:00Z"))
        .forecastAt(Instant.parse("2026-07-30T09:00:00Z"))
        .skyStatus(SkyStatus.CLEAR)
        .precipitationType(PrecipitationType.NONE)
        .build();
    entityManager.persist(first);
    entityManager.flush();

    Weather second = Weather.builder()
        .grid(grid)
        .forecastedAt(Instant.parse("2026-07-30T00:00:00Z"))
        .forecastAt(Instant.parse("2026-07-30T12:00:00Z"))
        .skyStatus(SkyStatus.CLOUDY)
        .precipitationType(PrecipitationType.RAIN)
        .build();

    // when & then
    assertThatCode(() -> {
      entityManager.persist(second);
      entityManager.flush();
    }).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("builder로 생성하면 필드가 그대로 채워진다")
  void fillsFieldsWhenBuilt() {
    // given
    Grid grid = persistGrid(60, 127);
    Instant forecastedAt = Instant.parse("2026-07-30T00:00:00Z");
    Instant forecastAt = Instant.parse("2026-07-30T09:00:00Z");

    // when
    Weather weather = Weather.builder()
        .grid(grid)
        .forecastedAt(forecastedAt)
        .forecastAt(forecastAt)
        .skyStatus(SkyStatus.CLEAR)
        .precipitationType(PrecipitationType.NONE)
        .precipitationAmount(0.0)
        .precipitationProbability(10.0)
        .humidityCurrent(55.0)
        .humidityComparedToDayBefore(-3.0)
        .temperatureCurrent(23.5)
        .temperatureComparedToDayBefore(1.2)
        .temperatureMin(18.0)
        .temperatureMax(26.0)
        .windSpeed(2.3)
        .build();

    // then
    assertThat(weather.getGrid()).isEqualTo(grid);
    assertThat(weather.getForecastedAt()).isEqualTo(forecastedAt);
    assertThat(weather.getForecastAt()).isEqualTo(forecastAt);
    assertThat(weather.getSkyStatus()).isEqualTo(SkyStatus.CLEAR);
    assertThat(weather.getPrecipitationType()).isEqualTo(PrecipitationType.NONE);
    assertThat(weather.getPrecipitationAmount()).isEqualTo(0.0);
    assertThat(weather.getPrecipitationProbability()).isEqualTo(10.0);
    assertThat(weather.getHumidityCurrent()).isEqualTo(55.0);
    assertThat(weather.getHumidityComparedToDayBefore()).isEqualTo(-3.0);
    assertThat(weather.getTemperatureCurrent()).isEqualTo(23.5);
    assertThat(weather.getTemperatureComparedToDayBefore()).isEqualTo(1.2);
    assertThat(weather.getTemperatureMin()).isEqualTo(18.0);
    assertThat(weather.getTemperatureMax()).isEqualTo(26.0);
    assertThat(weather.getWindSpeed()).isEqualTo(2.3);
  }

  @Test
  @DisplayName("windSpeed가 null이면 toDto의 speed는 0.0, asWord는 그 0.0에 대응하는 등급으로 일치한다")
  void toDtoKeepsSpeedAndAsWordConsistentWhenWindSpeedIsNull() {
    // given
    Grid grid = Grid.builder().x(60).y(127).build();
    Weather weather = Weather.builder()
        .grid(grid)
        .forecastedAt(Instant.parse("2026-07-30T00:00:00Z"))
        .forecastAt(Instant.parse("2026-07-30T09:00:00Z"))
        .skyStatus(SkyStatus.CLEAR)
        .precipitationType(PrecipitationType.NONE)
        .windSpeed(null)
        .build();

    // when
    var dto = weather.toDto(null);

    // then
    assertThat(dto.windSpeed().speed()).isEqualTo(0.0);
    assertThat(dto.windSpeed().asWord()).isEqualTo(WindStrength.fromSpeed(0.0));
  }
}