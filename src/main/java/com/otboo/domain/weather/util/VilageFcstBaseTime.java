package com.otboo.domain.weather.util;

import java.time.LocalDate;
import java.time.LocalTime;

public record VilageFcstBaseTime(LocalDate baseDate, LocalTime baseTime) {
}