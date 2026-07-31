package com.otboo.domain.weather.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public class VilageFcstBaseTimeResolver {

  //기상청 발표 시간
  private static final List<LocalTime> BASE_TIMES = List.of(
      LocalTime.of(2, 0),
      LocalTime.of(5, 0),
      LocalTime.of(8, 0),
      LocalTime.of(11, 0),
      LocalTime.of(14, 0),
      LocalTime.of(17, 0),
      LocalTime.of(20, 0),
      LocalTime.of(23, 0)
  );

  // api 사용 여유 시간 (10분)
  private static final Duration AVAILABILITY_DELAY = Duration.ofMinutes(10);

  public VilageFcstBaseTime resolve(LocalDateTime now) { // 현재시간에서 발표한지 가장 가까운 날씨 예보를 선택하기 위한 메서드
    for (int i = BASE_TIMES.size() - 1; i >= 0; i--) {
      LocalTime baseTime = BASE_TIMES.get(i);
      LocalDateTime availableAt = LocalDateTime.of(now.toLocalDate(), baseTime).plus(AVAILABILITY_DELAY);
      if (!now.isBefore(availableAt)) {
        return new VilageFcstBaseTime(now.toLocalDate(), baseTime);
      }
    }

    LocalTime lastSlotOfDay = BASE_TIMES.get(BASE_TIMES.size() - 1);
    return new VilageFcstBaseTime(now.toLocalDate().minusDays(1), lastSlotOfDay);
  }

  public VilageFcstBaseTime previous(VilageFcstBaseTime current) {
    int index = BASE_TIMES.indexOf(current.baseTime());
    if (index > 0) {
      return new VilageFcstBaseTime(current.baseDate(), BASE_TIMES.get(index - 1));
    }

    LocalTime lastSlotOfDay = BASE_TIMES.get(BASE_TIMES.size() - 1);
    return new VilageFcstBaseTime(current.baseDate().minusDays(1), lastSlotOfDay);
  }
}