package com.otboo.domain.weather.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.weather.dto.HumidityDto;
import com.otboo.domain.weather.dto.PrecipitationDto;
import com.otboo.domain.weather.dto.TemperatureDto;
import com.otboo.domain.weather.dto.WeatherDto;
import com.otboo.domain.weather.dto.WindSpeedDto;
import com.otboo.domain.weather.entity.PrecipitationType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DailyForecastSelectorTest {

  private final DailyForecastSelector selector = new DailyForecastSelector();

  @Test
  @DisplayName("하루치 슬롯만 있을 때, 지금 시각과 가장 가까운 슬롯을 대표값으로 고른다")
  void selectsClosestSlotWhenOnlyOneDayExists() {
    // given
    Instant now = Instant.parse("2026-07-31T05:00:00Z"); // 14:00 KST
    List<WeatherDto> forecasts = List.of(
        weatherDto(Instant.parse("2026-07-31T03:00:00Z")), // 12:00 KST, 2시간 차이
        weatherDto(Instant.parse("2026-07-31T06:00:00Z")), // 15:00 KST, 1시간 차이 (가장 가까움)
        weatherDto(Instant.parse("2026-07-31T09:00:00Z"))  // 18:00 KST, 4시간 차이
    );

    // when
    List<WeatherDto> result = selector.select(forecasts, now);

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).forecastAt()).isEqualTo(Instant.parse("2026-07-31T06:00:00Z"));
  }

  @Test
  @DisplayName("여러 날짜가 있을 때, 오늘의 대표 시각과 가장 가까운 슬롯을 각 날짜마다 골라 날짜순으로 반환한다")
  void selectsClosestSlotPerDateUsingTodayRepresentativeTime() {
    // given
    Instant now = Instant.parse("2026-07-31T05:00:00Z"); // 14:00 KST
    List<WeatherDto> forecasts = List.of(
        weatherDto(Instant.parse("2026-07-31T03:00:00Z")), // 오늘 12:00 KST
        weatherDto(Instant.parse("2026-07-31T06:00:00Z")), // 오늘 15:00 KST, 대표 시각(now와 1시간 차이로 최근접)
        weatherDto(Instant.parse("2026-07-31T09:00:00Z")), // 오늘 18:00 KST
        weatherDto(Instant.parse("2026-08-01T05:00:00Z")), // 내일 14:00 KST, 대표시각(15:00)과 1시간 차이 (최근접)
        weatherDto(Instant.parse("2026-08-01T08:00:00Z")), // 내일 17:00 KST, 대표시각과 2시간 차이
        weatherDto(Instant.parse("2026-08-02T06:00:00Z")), // 모레 15:00 KST, 대표시각과 정확히 일치
        weatherDto(Instant.parse("2026-08-02T09:00:00Z"))  // 모레 18:00 KST
    );

    // when
    List<WeatherDto> result = selector.select(forecasts, now);

    // then
    assertThat(result).extracting(WeatherDto::forecastAt).containsExactly(
        Instant.parse("2026-07-31T06:00:00Z"),
        Instant.parse("2026-08-01T05:00:00Z"),
        Instant.parse("2026-08-02T06:00:00Z")
    );
  }

  @Test
  @DisplayName("오늘 남은 미래 시간대가 하나도 없으면 가장 가까운 과거 시간대를 대표값으로 고른다")
  void fallsBackToClosestPastSlotWhenNoFutureSlotLeftToday() {
    // given
    Instant now = Instant.parse("2026-07-31T14:50:00Z"); // 23:50 KST, 오늘 남은 미래 슬롯 없음
    List<WeatherDto> forecasts = List.of(
        weatherDto(Instant.parse("2026-07-31T12:00:00Z")), // 21:00 KST, now와 2시간50분 차이
        weatherDto(Instant.parse("2026-07-31T14:00:00Z"))  // 23:00 KST, now와 50분 차이 (최근접)
    );

    // when
    List<WeatherDto> result = selector.select(forecasts, now);

    // then
    assertThat(result).extracting(WeatherDto::forecastAt).containsExactly(
        Instant.parse("2026-07-31T14:00:00Z")
    );
  }

  @Test
  @DisplayName("마지막 날 예보가 자정 한 줄뿐이면(연장예보 끝자락) 결과에서 제외한다")
  void excludesLastDateWhenOnlyOneSlotExists() {
    // given
    Instant now = Instant.parse("2026-07-31T05:00:00Z"); // 14:00 KST
    List<WeatherDto> forecasts = List.of(
        weatherDto(Instant.parse("2026-07-31T03:00:00Z")), // 오늘 12:00 KST
        weatherDto(Instant.parse("2026-07-31T06:00:00Z")), // 오늘 15:00 KST, 대표 시각
        weatherDto(Instant.parse("2026-07-31T09:00:00Z")), // 오늘 18:00 KST
        weatherDto(Instant.parse("2026-08-01T05:00:00Z")), // 내일 14:00 KST
        weatherDto(Instant.parse("2026-08-01T08:00:00Z")), // 내일 17:00 KST
        weatherDto(Instant.parse("2026-08-02T15:00:00Z"))  // 모레의 다음날(8/3) 00:00 KST, 이 날짜엔 이 한 줄뿐
    );

    // when
    List<WeatherDto> result = selector.select(forecasts, now);

    // then
    assertThat(result).extracting(WeatherDto::forecastAt).containsExactly(
        Instant.parse("2026-07-31T06:00:00Z"),
        Instant.parse("2026-08-01T05:00:00Z")
    );
  }

  @Test
  @DisplayName("마지막 날에 00:00 말고 다른 시각도 있으면 제외하지 않는다")
  void keepsLastDateWhenItHasMoreThanJustMidnight() {
    // given
    Instant now = Instant.parse("2026-07-31T05:00:00Z"); // 14:00 KST
    List<WeatherDto> forecasts = List.of(
        weatherDto(Instant.parse("2026-07-31T03:00:00Z")), // 오늘 12:00 KST
        weatherDto(Instant.parse("2026-07-31T06:00:00Z")), // 오늘 15:00 KST, 대표 시각
        weatherDto(Instant.parse("2026-07-31T09:00:00Z")), // 오늘 18:00 KST
        weatherDto(Instant.parse("2026-08-01T05:00:00Z")), // 내일 14:00 KST
        weatherDto(Instant.parse("2026-08-01T08:00:00Z")), // 내일 17:00 KST
        weatherDto(Instant.parse("2026-08-01T15:00:00Z")), // 모레 00:00 KST
        weatherDto(Instant.parse("2026-08-01T18:00:00Z"))  // 모레 03:00 KST, 대표시각(15:00)과 더 가까움
    );

    // when
    List<WeatherDto> result = selector.select(forecasts, now);

    // then
    assertThat(result).extracting(WeatherDto::forecastAt).containsExactly(
        Instant.parse("2026-07-31T06:00:00Z"),
        Instant.parse("2026-08-01T05:00:00Z"),
        Instant.parse("2026-08-01T18:00:00Z")
    );
  }

  @Test
  @DisplayName("날짜가 하나뿐이면 그마저 00:00 하나뿐이어도 제외하지 않는다")
  void keepsOnlyDateEvenWhenItIsMidnightOnly() {
    // given
    Instant now = Instant.parse("2026-07-30T15:00:00Z"); // 2026-07-31 00:00 KST
    List<WeatherDto> forecasts = List.of(
        weatherDto(Instant.parse("2026-07-30T15:00:00Z")) // 2026-07-31 00:00 KST, 이 날짜의 유일한 데이터
    );

    // when
    List<WeatherDto> result = selector.select(forecasts, now);

    // then
    assertThat(result).extracting(WeatherDto::forecastAt).containsExactly(
        Instant.parse("2026-07-30T15:00:00Z")
    );
  }

  private WeatherDto weatherDto(Instant forecastAt) {
    return weatherDto(forecastAt, 0.0);
  }

  private WeatherDto weatherDto(Instant forecastAt, double temperature) {
    return new WeatherDto(
        null,
        forecastAt,
        forecastAt,
        null,
        null,
        new PrecipitationDto(PrecipitationType.NONE, 0.0, 0.0),
        new HumidityDto(0.0, 0.0),
        new TemperatureDto(temperature, 0.0, 0.0, 0.0, 0.0),
        new WindSpeedDto(0.0, null)
    );
  }

  @Test
  @DisplayName("일별 대표값의 평균 기온은 대표 슬롯 하나가 아니라 그 날 모든 슬롯의 현재기온 평균이다")
  void averageTemperatureIsMeanOfAllSlotsInTheDayNotJustTheRepresentative() {
    // given: 오늘 12/15/18시, 대표 시각은 15시(now와 가장 가까움) - 대표 슬롯 값(26.0)과
    // 평균(10+26+30)/3=22.0이 서로 달라야 대표값을 그대로 쓰는 버그와 구분된다.
    Instant now = Instant.parse("2026-07-31T05:00:00Z"); // 14:00 KST
    List<WeatherDto> forecasts = List.of(
        weatherDto(Instant.parse("2026-07-31T03:00:00Z"), 10.0), // 12:00 KST
        weatherDto(Instant.parse("2026-07-31T06:00:00Z"), 26.0), // 15:00 KST, 대표 시각
        weatherDto(Instant.parse("2026-07-31T09:00:00Z"), 30.0)  // 18:00 KST
    );

    // when
    List<WeatherDto> result = selector.select(forecasts, now);

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).temperature().current()).isEqualTo(26.0);
    assertThat(result.get(0).temperature().average()).isEqualTo(22.0);
  }
}