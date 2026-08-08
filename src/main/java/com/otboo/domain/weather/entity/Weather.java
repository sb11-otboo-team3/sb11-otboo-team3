package com.otboo.domain.weather.entity;

import com.otboo.domain.weather.dto.HumidityDto;
import com.otboo.domain.weather.dto.PrecipitationDto;
import com.otboo.domain.weather.dto.TemperatureDto;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.dto.WeatherDto;
import com.otboo.domain.weather.dto.WeatherSummaryDto;
import com.otboo.domain.weather.dto.WindSpeedDto;
import com.otboo.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "weathers",
    uniqueConstraints = @UniqueConstraint(columnNames = {"grid_id", "forecast_at", "forecasted_at"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Weather extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "grid_id", nullable = false)
  private Grid grid;

  @Column(name = "forecasted_at", nullable = false)
  private Instant forecastedAt;

  @Column(name = "forecast_at", nullable = false)
  private Instant forecastAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "sky_status", nullable = false, length = 20)
  private SkyStatus skyStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "precipitation_type", nullable = false, length = 20)
  private PrecipitationType precipitationType;

  @Column(name = "precipitation_amount")
  private Double precipitationAmount;

  @Column(name = "precipitation_probability")
  private Double precipitationProbability;

  @Column(name = "humidity_current")
  private Double humidityCurrent;

  @Column(name = "humidity_compared_to_day_before")
  private Double humidityComparedToDayBefore;

  @Column(name = "temperature_current")
  private Double temperatureCurrent;

  @Column(name = "temperature_compared_to_day_before")
  private Double temperatureComparedToDayBefore;

  @Column(name = "temperature_min")
  private Double temperatureMin;

  @Column(name = "temperature_max")
  private Double temperatureMax;

  @Column(name = "wind_speed")
  private Double windSpeed;

  @Builder
  private Weather(
      Grid grid,
      Instant forecastedAt,
      Instant forecastAt,
      SkyStatus skyStatus,
      PrecipitationType precipitationType,
      Double precipitationAmount,
      Double precipitationProbability,
      Double humidityCurrent,
      Double humidityComparedToDayBefore,
      Double temperatureCurrent,
      Double temperatureComparedToDayBefore,
      Double temperatureMin,
      Double temperatureMax,
      Double windSpeed
  ) {
    this.grid = grid;
    this.forecastedAt = forecastedAt;
    this.forecastAt = forecastAt;
    this.skyStatus = skyStatus;
    this.precipitationType = precipitationType;
    this.precipitationAmount = precipitationAmount;
    this.precipitationProbability = precipitationProbability;
    this.humidityCurrent = humidityCurrent;
    this.humidityComparedToDayBefore = humidityComparedToDayBefore;
    this.temperatureCurrent = temperatureCurrent;
    this.temperatureComparedToDayBefore = temperatureComparedToDayBefore;
    this.temperatureMin = temperatureMin;
    this.temperatureMax = temperatureMax;
    this.windSpeed = windSpeed;
  }

  // dailyTemperatureMin/Max는 이 row 하나만으론 계산할 수 없음(그날 다른 슬롯들을 모아야 함) - 호출부에서 집계해서 넘겨줌
  public WeatherSummaryDto toSummaryDto(double dailyTemperatureMin, double dailyTemperatureMax) {
    return new WeatherSummaryDto(
        getId(),
        skyStatus,
        new PrecipitationDto(precipitationType, orElseZero(precipitationAmount), orElseZero(precipitationProbability)),
        new TemperatureDto(
            orElseZero(temperatureCurrent),
            orElseZero(temperatureComparedToDayBefore),
            dailyTemperatureMin,
            dailyTemperatureMax
        )
    );
  }

  // 응답 DTO로 변환하는 정규 변환 지점 - 서비스 쪽에서 필드 하나하나 재조립하지 않도록 여기 한 곳에 모아둠.
  // location은 요청자의 원본 좌표라 이 row(격자 단위)만으론 알 수 없어 호출부에서 넘겨받는다.
  public WeatherDto toDto(WeatherAPILocation location) {
    return new WeatherDto(
        getId(),
        forecastedAt,
        forecastAt,
        location,
        skyStatus,
        new PrecipitationDto(precipitationType, orElseZero(precipitationAmount), orElseZero(precipitationProbability)),
        new HumidityDto(orElseZero(humidityCurrent), orElseZero(humidityComparedToDayBefore)),
        new TemperatureDto(
            orElseZero(temperatureCurrent),
            orElseZero(temperatureComparedToDayBefore),
            orElseZero(temperatureMin != null ? temperatureMin : temperatureCurrent),
            orElseZero(temperatureMax != null ? temperatureMax : temperatureCurrent)
        ),
        new WindSpeedDto(orElseZero(windSpeed), WindStrength.fromSpeed(windSpeed))
    );
  }

  private double orElseZero(Double value) {
    return value != null ? value : 0.0;
  }
}
