package com.otboo.domain.weather.diff;

import java.time.Instant;

// 일일별 급변 하나(카테고리+시작 시각). rising은 기온일 때만 의미 있음(오름/내림 문구 구분용) -
// 강수·풍속은 애초에 악화 방향만 트리거되니 항상 true.
public record DailyDiffTrigger(
    DiffCategory category,
    Instant fromTime,
    boolean rising
) {
}
