package com.otboo.domain.weather.diff;

import com.otboo.domain.weather.entity.Grid;
import java.time.LocalDate;
import java.util.Set;

// 일일별 급변 감지 신호. WeatherAnnouncementDiffEvent와 같은 패턴 - "감지"와 "누구에게 어떻게
// 알릴지"를 분리해서, 실제 알림 발행은 이 이벤트를 구독하는 별도 컴포넌트(추후 추가)가 담당한다.
public record WeatherDailyDiffEvent(
    Grid grid,
    LocalDate date,
    Set<DiffCategory> triggeredCategories
) {
}
