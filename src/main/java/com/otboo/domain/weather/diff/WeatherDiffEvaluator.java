package com.otboo.domain.weather.diff;

import com.otboo.domain.weather.entity.PrecipitationType;
import org.springframework.stereotype.Component;

// 급변 카테고리별 순수 판정 로직. DB/Spring 컨텍스트 없이 이전/현재 값만 비교한다 -
// 발표별(WeatherPersister)·일일별(WeatherDailyDiffScheduler) 양쪽에서 재사용할 수 있게
// 배선 로직과 분리해뒀다.
@Component
public class WeatherDiffEvaluator {

  // 기온은 대칭(|Δ|) - 오르든 내리든 옷차림에 똑같이 영향을 주므로 방향 구분 없이 판정한다.
  public boolean isTemperatureTriggered(double previousTemp, double currentTemp, WeatherDiffProperties properties) {
    return Math.abs(currentTemp - previousTemp) >= properties.announcementTempThreshold();
  }

  // 강수는 악화 방향(NONE→강수)만 판정한다 - 그치는 건 몰라도 무해하지만 시작되는 건 놓치면 곤란하므로.
  public boolean isPrecipitationTriggered(PrecipitationType previousType, PrecipitationType currentType) {
    return previousType == PrecipitationType.NONE && currentType != PrecipitationType.NONE;
  }
}
