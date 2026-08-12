package com.otboo.domain.weather.batch;

import com.otboo.domain.weather.dto.VilageFcstItem;
import com.otboo.domain.weather.entity.Grid;
import java.util.List;

// GridForecastProcessor가 격자 하나를 기상청 조회 결과로 바꾼 산출물. GridForecastWriter가 저장할 때
// grid가 필요하므로(Weather는 grid 없이 저장할 수 없음) 항목 리스트만이 아니라 grid까지 함께 들고 다닌다.
public record GridForecast(Grid grid, List<VilageFcstItem> items) {
}
