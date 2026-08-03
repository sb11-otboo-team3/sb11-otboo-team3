package com.otboo.domain.weather.util;

import java.time.LocalDate;
import java.time.LocalTime;

// 발표 시각
public record VilageFcstBaseTime(
    LocalDate baseDate,
    LocalTime baseTime
) {
}