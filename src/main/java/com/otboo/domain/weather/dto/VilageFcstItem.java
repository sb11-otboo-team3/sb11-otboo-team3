package com.otboo.domain.weather.dto;

import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.entity.WindStrength;
import java.time.LocalDateTime;
import java.time.ZoneId;

// 기상청 api에서 필요한 데이터만 담은 dto
public record VilageFcstItem(
    LocalDateTime forecastedAt,
    LocalDateTime forecastAt,
    SkyStatus skyStatus,
    PrecipitationType precipitationType,
    Double precipitationAmount,
    Double precipitationProbability,
    Double humidity,
    Double temperature,
    Double temperatureMin,
    Double temperatureMax,
    Double windSpeed
) {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  // 아직 저장 전(id 없음)인 상태에서 응답 DTO가 필요할 때(동시 저장 충돌로 재조회했는데도 못 찾은 극단적인 경우) 쓰는 변환.
  // id는 아직 없으니 null, comparedToDayBefore는 저장 단계에서만 계산되니 0.0으로 둔다.
  public WeatherDto toDto(WeatherAPILocation location) {
    // windStrength 필드는 windSpeed로부터 파생된 값이라 따로 저장해두면 어긋날 여지가 생긴다
    // (windSpeed가 null이면 speed=0.0인데 필드값 windStrength는 null) - 보정된 값으로 다시 계산해서 둘을 맞춘다.
    double correctedWindSpeed = orElseZero(windSpeed);
    return new WeatherDto(
        null,
        forecastedAt.atZone(KST).toInstant(),
        forecastAt.atZone(KST).toInstant(),
        location,
        skyStatus,
        new PrecipitationDto(precipitationType, orElseZero(precipitationAmount), orElseZero(precipitationProbability)),
        new HumidityDto(orElseZero(humidity), 0.0),
        new TemperatureDto(
            orElseZero(temperature),
            0.0,
            orElseZero(temperatureMin != null ? temperatureMin : temperature),
            orElseZero(temperatureMax != null ? temperatureMax : temperature)
        ),
        new WindSpeedDto(correctedWindSpeed, WindStrength.fromSpeed(correctedWindSpeed))
    );
  }

  private static double orElseZero(Double value) {
    return value != null ? value : 0.0;
  }
}