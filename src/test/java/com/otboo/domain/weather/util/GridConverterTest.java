package com.otboo.domain.weather.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.domain.weather.exception.InvalidWeatherGridException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GridConverterTest {

  private final GridConverter gridConverter = new GridConverter();

  @Nested
  @DisplayName("성공 케이스")
  class Success {

    @Test
    @DisplayName("서울시청 좌표를 기상청 격자좌표로 변환하면 (60, 127)이다")
    void convertsSeoulCityHallGrid() {
      // given
      double latitude = 37.5665;
      double longitude = 126.9780;

      // when
      WeatherGrid grid = gridConverter.convert(latitude, longitude);

      // then
      assertThat(grid.x()).isEqualTo(60);
      assertThat(grid.y()).isEqualTo(127);
    }

    @Test
    @DisplayName("부산시청 좌표를 기상청 격자좌표로 변환하면 (98, 76)이다")
    void convertsBusanCityHallCoordinate() {
      // given
      double latitude = 35.1796;
      double longitude = 129.0756;

      // when
      WeatherGrid grid = gridConverter.convert(latitude, longitude);

      // then
      assertThat(grid.x()).isEqualTo(98);
      assertThat(grid.y()).isEqualTo(76);
    }

    @Test
    @DisplayName("제주시청 좌표를 기상청 격자좌표로 변환하면 (53, 38)이다")
    void convertsJejuCityHallCoordinate() {
      // given
      double latitude = 33.4996;
      double longitude = 126.5312;

      // when
      WeatherGrid grid = gridConverter.convert(latitude, longitude);

      // then
      assertThat(grid.x()).isEqualTo(53);
      assertThat(grid.y()).isEqualTo(38);
    }
  }

  @Nested
  @DisplayName("실패 케이스")
  class Failure {

    @Test
    @DisplayName("위도가 유효 범위(-90~90)를 벗어나면 예외를 던진다")
    void throwsExceptionWhenLatitudeOutOfRange() {
      // given
      double latitude = 91.0;
      double longitude = 126.9780;

      // when & then
      assertThatThrownBy(() -> gridConverter.convert(latitude, longitude))
          .isInstanceOf(InvalidWeatherGridException.class);
    }

    @Test
    @DisplayName("경도가 유효 범위(-180~180)를 벗어나면 예외를 던진다")
    void throwsExceptionWhenLongitudeOutOfRange() {
      // given
      double latitude = 37.5665;
      double longitude = 181.0;

      // when & then
      assertThatThrownBy(() -> gridConverter.convert(latitude, longitude))
          .isInstanceOf(InvalidWeatherGridException.class);
    }

    @Test
    @DisplayName("유효 범위 안이지만 기상청 격자 범위를 벗어나면 예외를 던진다")
    void throwsExceptionWhenOutsideWeatherGridBounds() {
      // given:  위경도 자체는 유효하지만 기상청 격자(대한민국) 범위를 벗어남
      double latitude = 40.7128;
      double longitude = -74.0060;

      // when & then
      assertThatThrownBy(() -> gridConverter.convert(latitude, longitude))
          .isInstanceOf(InvalidWeatherGridException.class);
    }
  }
}