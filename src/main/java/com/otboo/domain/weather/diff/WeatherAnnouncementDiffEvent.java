package com.otboo.domain.weather.diff;

import com.otboo.domain.weather.entity.Weather;
import java.util.Set;

// 발표별 급변 감지 신호. WeatherPersister가 upsert 전후로 이전/현재 값을 비교해서 발행하고,
// 실제로 누구에게 무슨 알림을 보낼지는 이 이벤트를 구독하는 별도 컴포넌트(추후 추가)가 담당한다 -
// NotificationEvent/NotificationEventListener와 같은 분리 구조.
public record WeatherAnnouncementDiffEvent(
    Weather previous,
    Weather current,
    Set<DiffCategory> triggeredCategories
) {
}
