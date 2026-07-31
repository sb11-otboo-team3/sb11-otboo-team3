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
    void convertsBusanCityHallGrid() {
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
    void convertsJejuCityHallGrid() {
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
    @DisplayName("위도가 90을 초과하면 예외를 던진다")
    void throwsExceptionWhenLatitudeAboveUpperBound() {
      // given
      double latitude = 91.0;
      double longitude = 126.9780;

      // when & then
      assertThatThrownBy(() -> gridConverter.convert(latitude, longitude))
          .isInstanceOf(InvalidWeatherGridException.class);
    }

    @Test
    @DisplayName("위도가 -90 미만이면 예외를 던진다")
    void throwsExceptionWhenLatitudeBelowLowerBound() {
      // given
      double latitude = -91.0;
      double longitude = 126.9780;

      // when & then
      assertThatThrownBy(() -> gridConverter.convert(latitude, longitude))
          .isInstanceOf(InvalidWeatherGridException.class);
    }

    @Test
    @DisplayName("경도가 180을 초과하면 예외를 던진다")
    void throwsExceptionWhenLongitudeAboveUpperBound() {
      // given
      double latitude = 37.5665;
      double longitude = 181.0;

      // when & then
      assertThatThrownBy(() -> gridConverter.convert(latitude, longitude))
          .isInstanceOf(InvalidWeatherGridException.class);
    }

    @Test
    @DisplayName("경도가 -180 미만이면 예외를 던진다")
    void throwsExceptionWhenLongitudeBelowLowerBound() {
      // given
      double latitude = 37.5665;
      double longitude = -181.0;

      // when & then
      assertThatThrownBy(() -> gridConverter.convert(latitude, longitude))
          .isInstanceOf(InvalidWeatherGridException.class);
    }

    @Test
    @DisplayName("계산된 격자 x가 최솟값보다 작으면 예외를 던진다")
    void throwsExceptionWhenGridXBelowLowerBound() {
      // given: 런던 - 위경도는 유효하지만 격자 x가 1 미만으로 계산됨
      double latitude = 51.5074;
      double longitude = -0.1278;

      // when & then
      assertThatThrownBy(() -> gridConverter.convert(latitude, longitude))
          .isInstanceOf(InvalidWeatherGridException.class);
    }

    @Test
    @DisplayName("계산된 격자 x가 최댓값보다 크면 예외를 던진다")
    void throwsExceptionWhenGridXAboveUpperBound() {
      // given: 도쿄 - 위경도는 유효하지만 격자 x가 149 초과로 계산됨
      double latitude = 35.6762;
      double longitude = 139.6503;

      // when & then
      assertThatThrownBy(() -> gridConverter.convert(latitude, longitude))
          .isInstanceOf(InvalidWeatherGridException.class);
    }

    @Test
    @DisplayName("계산된 격자 y가 최솟값보다 작으면 예외를 던진다")
    void throwsExceptionWhenGridYBelowLowerBound() {
      // given: 경도는 기준 경도(126)와 동일, 위도만 적도 이남으로 이동해 격자 y가 1 미만으로 계산됨
      double latitude = -10.0;
      double longitude = 126.0;

      // when & then
      assertThatThrownBy(() -> gridConverter.convert(latitude, longitude))
          .isInstanceOf(InvalidWeatherGridException.class);
    }

    @Test
    @DisplayName("계산된 격자 y가 최댓값보다 크면 예외를 던진다")
    void throwsExceptionWhenGridYAboveUpperBound() {
      // given: 경도는 기준 경도(126)와 동일, 위도만 시베리아 방향으로 이동해 격자 y가 253 초과로 계산됨
      double latitude = 66.5;
      double longitude = 126.0;

      // when & then
      assertThatThrownBy(() -> gridConverter.convert(latitude, longitude))
          .isInstanceOf(InvalidWeatherGridException.class);
    }

    @Test
    @DisplayName("기준 경도에서 180도 이상 서쪽으로 떨어진 좌표는 음의 wraparound 처리 후에도 격자 범위를 벗어나 예외를 던진다")
    void throwsExceptionWhenLongitudeWrapsAroundNegatively() {
      // given: 뉴욕 - theta가 -PI 미만이 되어 wraparound 보정이 일어나는 케이스
      double latitude = 40.7128;
      double longitude = -74.0060;

      // when & then
      assertThatThrownBy(() -> gridConverter.convert(latitude, longitude))
          .isInstanceOf(InvalidWeatherGridException.class);
    }
  }
}