package com.otboo.domain.weather.kafka;

// 프리페치 프로듀서가 발행하는 요청 메시지. 격자(x,y)만 담고 발표시각(baseTime)은 컨슈머가 소비 시점에
// 다시 계산한다 - 처리가 늦어져도 항상 그 순간 기준 최신 발표분을 가져오게 하기 위함.
public record GridForecastRequestedMessage(int x, int y) {
}
