package com.otboo.domain.weather.entity;

import com.otboo.domain.weather.dto.PrecipitationDto;
import com.otboo.domain.weather.dto.TemperatureDto;
import com.otboo.domain.weather.dto.WeatherSummaryDto;
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

  private double orElseZero(Double value) {
    return value != null ? value : 0.0;
  }
}
