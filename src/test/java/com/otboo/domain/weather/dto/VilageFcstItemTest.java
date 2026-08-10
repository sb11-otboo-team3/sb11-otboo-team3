package com.otboo.domain.weather.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.entity.WindStrength;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VilageFcstItemTest {

  private final LocalDateTime forecastedAt = LocalDateTime.of(2026, 7, 30, 5, 0);
  private final LocalDateTime forecastAt = LocalDateTime.of(2026, 7, 30, 15, 0);

  @Test
  @DisplayName("windSpeed가 null이면 toDto의 speed는 0.0, asWord는 그 0.0에 대응하는 등급으로 일치한다")
  void toDtoKeepsSpeedAndAsWordConsistentWhenWindSpeedIsNull() {
    // given
    VilageFcstItem item = new VilageFcstItem(
        forecastedAt, forecastAt, SkyStatus.CLEAR, PrecipitationType.NONE,
        0.0, 10.0, 55.0, 23.5, 18.0, 26.0,
        null
    );

    // when
    WeatherDto dto = item.toDto(null);

    // then
    assertThat(dto.windSpeed().speed()).isEqualTo(0.0);
    assertThat(dto.windSpeed().asWord()).isEqualTo(WindStrength.fromSpeed(0.0));
  }
}
